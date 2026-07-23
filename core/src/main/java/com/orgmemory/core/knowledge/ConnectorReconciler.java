package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.ConnectorIdentityResolution.ResolvedPrincipal;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectStorageException;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectWriteRequest;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Reconciles one crawl object (or tombstone) into the governed ledger. PostgreSQL preparation,
 * OpenFGA publication, and PostgreSQL completion use separate committed phases. Identity
 * observation and matching resolve once per batch; every subsequent object reuses that
 * resolution to translate the permission payload into sealed ACL entries and membership.
 */
@Service
class ConnectorReconciler {

    private static final String PIPELINE_VERSION = "connector-pipeline-v1";
    private static final String NORMALIZER_VERSION = "connector-normalizer-v1";
    private static final String PARSER_VERSION = "connector-parser-v1";
    private static final String POLICY_VERSION = "connector-staging-v1";
    private static final Duration ACL_TTL = Duration.ofHours(23);

    private final KnowledgeIngestionService ingestion;
    private final SourcePrincipalService principals;
    private final SourcePrincipalMappingService mappings;
    private final SourcePrincipalRepository principalRepository;
    private final KnowledgeAssetPublicationService publications;
    private final ObjectProvider<ConnectorTextEmbedder> embedder;
    private final ObjectProvider<KnowledgeTextChunker> chunker;
    private final ObjectStoragePort objects;
    private final SourceObjectRepository sources;
    private final ConnectorSourceRevisionCoordinator revisionCoordinator;
    private final PermissionAuditService audit;

    ConnectorReconciler(
            KnowledgeIngestionService ingestion,
            SourcePrincipalService principals,
            SourcePrincipalMappingService mappings,
            SourcePrincipalRepository principalRepository,
            KnowledgeAssetPublicationService publications,
            ObjectProvider<ConnectorTextEmbedder> embedder,
            ObjectProvider<KnowledgeTextChunker> chunker,
            ObjectStoragePort objects,
            SourceObjectRepository sources,
            ConnectorSourceRevisionCoordinator revisionCoordinator,
            PermissionAuditService audit) {
        this.ingestion = ingestion;
        this.principals = principals;
        this.mappings = mappings;
        this.principalRepository = principalRepository;
        this.publications = publications;
        this.embedder = embedder;
        this.chunker = chunker;
        this.objects = objects;
        this.sources = sources;
        this.revisionCoordinator = revisionCoordinator;
        this.audit = audit;
    }

    /**
     * Observes every identity in the batch, runs the matcher on source users, and resolves
     * group memberships. Runs once per batch; the returned resolution is reused by every
     * object reconcile.
     */
    ConnectorIdentityResolution resolveIdentities(ConnectorIngestionContext ctx, ConnectorCrawlBatch batch) {
        Instant now = Instant.now();
        Map<String, ResolvedPrincipal> byKey = new LinkedHashMap<>();
        for (ConnectorIdentityItem item : batch.identities()) {
            SourcePrincipal principal = principals.observe(new SourceIdentityObservation(
                    ctx.organizationId(),
                    ctx.sourceSystem(),
                    ctx.sourceConnectionKey(),
                    item.externalKey(),
                    item.kind(),
                    item.email(),
                    item.displayName(),
                    item.ssoVerified(),
                    item.idpIssuer(),
                    item.idpSubject(),
                    now));
            byKey.put(item.externalKey(), new ResolvedPrincipal(principal.getId(), principal.getKind()));
            if (item.kind() == SourcePrincipalKind.SOURCE_USER) {
                mappings.autoMap(principal, item.idpIssuer(), item.idpSubject());
            }
        }
        Map<String, List<UUID>> membersByGroup = new LinkedHashMap<>();
        for (ConnectorIdentityItem item : batch.identities()) {
            if (item.kind() != SourcePrincipalKind.SOURCE_GROUP) {
                continue;
            }
            List<UUID> memberIds = new ArrayList<>();
            for (String memberKey : item.memberExternalKeys()) {
                ResolvedPrincipal member = byKey.get(memberKey);
                if (member == null) {
                    member = lookupRegistry(ctx, memberKey);
                }
                if (member == null || member.kind() != SourcePrincipalKind.SOURCE_USER) {
                    throw new IllegalArgumentException(
                            "group " + item.externalKey() + " member is not an observed source user: " + memberKey);
                }
                memberIds.add(member.id());
            }
            membersByGroup.put(item.externalKey(), List.copyOf(memberIds));
        }
        return new ConnectorIdentityResolution(byKey, membersByGroup);
    }

    /**
     * Reconciles one content object. New or changed content creates a new immutable source
     * revision and Knowledge Asset version; unchanged content rotates only the ACL generation.
     */
    ObjectOutcome reconcile(
            ConnectorIngestionContext ctx,
            ConnectorContentItem content,
            ConnectorPermissionItem permission,
            ConnectorIdentityResolution resolution) {
        AclPlan plan = buildAclPlan(ctx, permission, resolution);
        var head = ingestion.findConnectorHead(
                ctx.organizationId(), ctx.sourceSystem(), ctx.sourceConnectionKey(), content.externalObjectId());
        boolean contentChanged = head.isEmpty()
                || !content.contentRevision().equals(head.get().currentContentRevision());
        if (contentChanged) {
            RawSourceRef raw = ingestion.registerConnectorSource(
                    registerCommand(
                            ctx,
                            content,
                            plan.entries(),
                            Instant.now().plus(ACL_TTL),
                            head.map(ConnectorHeadView::currentSnapshotId).orElse(null)),
                    plan.membership());
            NormalizedRecordRef normalized = ingestion.normalize(new NormalizeRawSourceCommand(
                    ctx.organizationId(),
                    raw.rawSourceObjectId(),
                    NORMALIZER_VERSION,
                    content.title(),
                    content.body(),
                    "und"));
            materializeContent(ctx, content, raw, normalized);
            audit(ctx, "CONNECTOR_MATERIALIZE", content.externalObjectId(), "OBJECT_MATERIALIZED");
            return ObjectOutcome.MATERIALIZED;
        }
        return rotate(ctx, head.get(), plan, content.externalObjectId());
    }

    /**
     * Reconciles an object that arrived in the permissions payload without content — a
     * permissions-only re-crawl on its own cadence. It rotates the existing object's ACL to a
     * new sealed generation; an object with no materialized content yet cannot be established
     * from permissions alone and is rejected.
     */
    ObjectOutcome reconcilePermissions(
            ConnectorIngestionContext ctx,
            ConnectorPermissionItem permission,
            ConnectorIdentityResolution resolution) {
        AclPlan plan = buildAclPlan(ctx, permission, resolution);
        var head = ingestion.findConnectorHead(
                ctx.organizationId(), ctx.sourceSystem(), ctx.sourceConnectionKey(), permission.externalObjectId());
        if (head.isEmpty()) {
            throw new IllegalArgumentException(
                    "permissions arrived for an object with no materialized content: "
                            + permission.externalObjectId());
        }
        return rotate(ctx, head.get(), plan, permission.externalObjectId());
    }

    private ObjectOutcome rotate(
            ConnectorIngestionContext ctx,
            ConnectorHeadView current,
            AclPlan plan,
            String externalObjectId) {
        ingestion.rotateConnectorAcl(
                new RotateSourceAclCommand(
                        ctx.organizationId(),
                        current.rawSourceObjectId(),
                        AclCaptureStatus.COMPLETE,
                        AccessGate.DENY,
                        Instant.now().plus(ACL_TTL),
                        plan.entries(),
                        current.currentSnapshotId()),
                plan.membership());
        audit(ctx, "CONNECTOR_ROTATE", externalObjectId, "ACL_ROTATED");
        return ObjectOutcome.ROTATED;
    }

    private AclPlan buildAclPlan(
            ConnectorIngestionContext ctx,
            ConnectorPermissionItem permission,
            ConnectorIdentityResolution resolution) {
        List<SourceAclEntryCommand> entries = new ArrayList<>();
        List<SealedGroupMembership> membership = new ArrayList<>();
        for (ConnectorAclGrant grant : grantsOf(permission)) {
            ResolvedPrincipal principal = resolve(ctx, grant.principalExternalKey(), resolution);
            if (principal.kind() != grant.principalKind()) {
                throw new IllegalArgumentException(
                        "grant principal kind does not match the observed identity: "
                                + grant.principalExternalKey());
            }
            SourcePrincipalType type = grant.principalKind() == SourcePrincipalKind.SOURCE_GROUP
                    ? SourcePrincipalType.SOURCE_GROUP
                    : SourcePrincipalType.SOURCE_USER;
            entries.add(new SourceAclEntryCommand(type, principal.id().toString(), grant.gate()));
            if (grant.principalKind() == SourcePrincipalKind.SOURCE_GROUP) {
                membership.add(new SealedGroupMembership(
                        principal.id(),
                        resolution.memberPrincipalIdsByGroupKey()
                                .getOrDefault(grant.principalExternalKey(), List.of())));
            }
        }
        return new AclPlan(entries, membership);
    }

    private record AclPlan(List<SourceAclEntryCommand> entries, List<SealedGroupMembership> membership) {
    }

    /** Retires a tombstoned object from retrieval. Returns whether an active object was retired. */
    boolean retire(ConnectorIngestionContext ctx, ConnectorTombstone tombstone) {
        var existing = sources.findByOrganizationIdAndSourceTypeAndSourceConnectionKeyAndExternalObjectId(
                ctx.organizationId(),
                SlackConnectorProfile.SOURCE_TYPE,
                ctx.sourceConnectionKey(),
                tombstone.externalObjectId());
        if (existing.isEmpty() || existing.get().getStatus() != SourceObjectStatus.ACTIVE) {
            return false;
        }
        SourceObject source = existing.get();
        source.archive();
        sources.saveAndFlush(source);
        audit(ctx, "CONNECTOR_RETIRE", tombstone.externalObjectId(), "OBJECT_RETIRED");
        return true;
    }

    private void materializeContent(
            ConnectorIngestionContext ctx,
            ConnectorContentItem content,
            RawSourceRef raw,
            NormalizedRecordRef normalized) {
        byte[] bytes = content.body().getBytes(StandardCharsets.UTF_8);
        String contentSha256 = sha256(content.body());
        ConnectorRevisionDraft draft = revisionCoordinator
                .findExisting(ctx, content, contentSha256)
                .orElse(null);
        ObjectKey key = new ObjectKey("organizations/" + ctx.organizationId()
                + "/connector/" + ctx.sourceSystem()
                + "/" + ctx.sourceConnectionKey()
                + "/" + content.externalObjectId()
                + "/" + content.contentRevision());
        if (draft == null) {
            UUID sourceId = UUID.randomUUID();
            UUID revisionId = UUID.randomUUID();
            UUID blobId = UUID.randomUUID();
            StoredObject stored = objects.put(
                    new ObjectWriteRequest(
                            key,
                            bytes.length,
                            SlackConnectorProfile.MEDIA_TYPE,
                            Map.of(
                                    "organization-id", ctx.organizationId().toString(),
                                    "source-object-id", sourceId.toString(),
                                    "source-revision-id", revisionId.toString())),
                    new ByteArrayInputStream(bytes));
            try {
                draft = revisionCoordinator.stage(
                        ctx, content, sourceId, revisionId, blobId, stored);
            } catch (RuntimeException failure) {
                try {
                    objects.delete(key);
                } catch (ObjectStorageException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        KnowledgeTextChunker resolvedChunker = requireChunker();
        List<KnowledgeTextChunk> chunks = resolvedChunker.split(List.of(
                new KnowledgeTextDocument(content.body(), null, null)));
        List<String> texts = chunks.stream().map(KnowledgeTextChunk::content).toList();
        ConnectorEmbeddingResult embedding = requireEmbedder().embed(ctx.organizationId(), texts);
        List<float[]> vectors = embedding.vectors();
        if (vectors.size() != texts.size()) {
            throw new IllegalStateException("connector embedding count did not match the chunk count");
        }
        List<KnowledgeChunkDraft> chunkDrafts = new ArrayList<>(texts.size());
        for (int index = 0; index < texts.size(); index++) {
            KnowledgeTextChunk chunk = chunks.get(index);
            chunkDrafts.add(new KnowledgeChunkDraft(
                    index,
                    chunk.content(),
                    sha256(chunk.content()),
                    null,
                    chunk.startPage(),
                    chunk.endPage(),
                    null,
                    vectors.get(index)));
        }
        KnowledgeAssetRef asset = publications.publish(new PublishKnowledgeAssetCommand(
                ctx.organizationId(),
                ctx.knowledgeSpaceId(),
                draft.sourceObjectId(),
                draft.sourceRevisionId(),
                normalized.normalizedRecordId(),
                ctx.actorUserId(),
                embedding.profile(),
                PIPELINE_VERSION,
                chunkDrafts));
        revisionCoordinator.complete(
                draft,
                PIPELINE_VERSION,
                PARSER_VERSION,
                resolvedChunker.version(),
                embedding.profile(),
                raw,
                normalized,
                asset);
    }

    private RegisterRawSourceCommand registerCommand(
            ConnectorIngestionContext ctx,
            ConnectorContentItem content,
            List<SourceAclEntryCommand> entries,
            Instant validUntil,
            UUID expectedCurrentSnapshotId) {
        return new RegisterRawSourceCommand(
                ctx.organizationId(),
                null,
                ctx.sourceSystem(),
                ctx.sourceConnectionKey(),
                content.externalObjectId(),
                content.contentRevision(),
                SlackConnectorProfile.OBJECT_TYPE,
                content.title(),
                content.body(),
                null,
                null,
                SlackConnectorProfile.CLASSIFICATION,
                SlackConnectorProfile.DECLARED_ACCESS,
                AclCaptureStatus.COMPLETE,
                AccessGate.DENY,
                validUntil,
                entries,
                expectedCurrentSnapshotId);
    }

    private ResolvedPrincipal resolve(
            ConnectorIngestionContext ctx, String externalKey, ConnectorIdentityResolution resolution) {
        ResolvedPrincipal resolved = resolution.principalsByKey().get(externalKey);
        if (resolved != null) {
            return resolved;
        }
        ResolvedPrincipal fromRegistry = lookupRegistry(ctx, externalKey);
        if (fromRegistry == null) {
            throw new IllegalArgumentException("grant references an unobserved principal: " + externalKey);
        }
        return fromRegistry;
    }

    private ResolvedPrincipal lookupRegistry(ConnectorIngestionContext ctx, String externalKey) {
        return principalRepository
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalKey(
                        ctx.organizationId(), ctx.sourceSystem(), ctx.sourceConnectionKey(), externalKey)
                .map(principal -> new ResolvedPrincipal(principal.getId(), principal.getKind()))
                .orElse(null);
    }

    private ConnectorTextEmbedder requireEmbedder() {
        ConnectorTextEmbedder resolved = embedder.getIfAvailable();
        if (resolved == null) {
            throw new IllegalStateException("connector text embedder is not configured");
        }
        return resolved;
    }

    private KnowledgeTextChunker requireChunker() {
        KnowledgeTextChunker resolved = chunker.getIfAvailable();
        if (resolved == null) {
            throw new IllegalStateException("knowledge text chunker is not configured");
        }
        return resolved;
    }

    private void audit(
            ConnectorIngestionContext ctx, String operation, String externalObjectId, String reasonCode) {
        audit.record(new PermissionAuditCommand(
                ctx.organizationId(),
                ctx.actorUserId(),
                operation,
                "SOURCE_OBJECT",
                externalObjectId,
                PermissionAuditDecision.ALLOW,
                reasonCode,
                POLICY_VERSION,
                ctx.crawlCursor(),
                null));
    }

    private static List<ConnectorAclGrant> grantsOf(ConnectorPermissionItem permission) {
        return permission == null ? List.of() : permission.grants();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    enum ObjectOutcome {
        MATERIALIZED,
        ROTATED
    }
}
