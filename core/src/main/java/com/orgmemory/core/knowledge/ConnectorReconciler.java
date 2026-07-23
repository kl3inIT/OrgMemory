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
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Reconciles one crawl object (or tombstone) into the governed ledger. Each entry point must
 * run inside a caller-supplied transaction ({@link ConnectorIngestionService} wraps each
 * object in its own so a per-object failure is isolated and atomic — the sealed ACL, head,
 * and materialized content commit together or not at all). Identity observation and matching
 * resolve once per batch here too; every subsequent object reuses that resolution to translate
 * the permission payload into sealed ACL entries and membership.
 */
@Service
class ConnectorReconciler {

    private static final String PIPELINE_VERSION = "connector-pipeline-v1";
    private static final String NORMALIZER_VERSION = "connector-normalizer-v1";
    private static final String PARSER_VERSION = "connector-parser-v1";
    private static final String CHUNKER_VERSION = "connector-chunker-v1";
    private static final String POLICY_VERSION = "connector-staging-v1";
    private static final Duration ACL_TTL = Duration.ofHours(23);
    private static final long PROJECTION_GENERATION = 1L;
    private static final int MAX_CHUNK_CHARS = 4000;
    private static final int MAX_CHUNKS = 200;

    private final KnowledgeIngestionService ingestion;
    private final SourcePrincipalService principals;
    private final SourcePrincipalMappingService mappings;
    private final SourcePrincipalRepository principalRepository;
    private final SourceConnectionRepository connections;
    private final KnowledgeAssetPublicationService publications;
    private final ObjectProvider<ConnectorTextEmbedder> embedder;
    private final ObjectStoragePort objects;
    private final SourceObjectRepository sources;
    private final ConnectorSourceRevisionCoordinator revisionCoordinator;
    private final PermissionAuditService audit;

    ConnectorReconciler(
            KnowledgeIngestionService ingestion,
            SourcePrincipalService principals,
            SourcePrincipalMappingService mappings,
            SourcePrincipalRepository principalRepository,
            SourceConnectionRepository connections,
            KnowledgeAssetPublicationService publications,
            ObjectProvider<ConnectorTextEmbedder> embedder,
            ObjectStoragePort objects,
            SourceObjectRepository sources,
            ConnectorSourceRevisionCoordinator revisionCoordinator,
            PermissionAuditService audit) {
        this.ingestion = ingestion;
        this.principals = principals;
        this.mappings = mappings;
        this.principalRepository = principalRepository;
        this.connections = connections;
        this.publications = publications;
        this.embedder = embedder;
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
        SourceIdentityTrust connectionTrust = identityTrustOf(ctx);
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
                mappings.autoMap(principal, item.idpIssuer(), item.idpSubject(), connectionTrust);
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
     * The administrator's standing decision for this connection, resolved once per batch rather
     * than per principal. A connection only has a row here once somebody has ruled on it, so an
     * absent row is the untrusted default: silence is not an attestation.
     */
    private SourceIdentityTrust identityTrustOf(ConnectorIngestionContext ctx) {
        return connections
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKey(
                        ctx.organizationId(), ctx.sourceSystem(), ctx.sourceConnectionKey())
                .map(SourceConnection::getIdentityTrust)
                .orElse(SourceIdentityTrust.UNTRUSTED);
    }

    /**
     * Reconciles one content object. A new object is materialized (raw + sealed ACL + normalize
     * + chunks + publish). An existing object whose content revision is unchanged converges its
     * ACL to a new sealed generation via head rotation. A changed content revision materializes
     * a new source revision on the same object, which is what makes an edited message stop
     * being served from the text the crawl first saw.
     */
    ObjectOutcome reconcile(
            ConnectorIngestionContext ctx,
            ConnectorContentItem content,
            ConnectorPermissionItem permission,
            ConnectorIdentityResolution resolution) {
        AclPlan plan = buildAclPlan(ctx, permission, resolution);
        var head = ingestion.findConnectorHead(
                ctx.organizationId(), ctx.sourceSystem(), ctx.sourceConnectionKey(), content.externalObjectId());
        if (head.isEmpty()) {
            // The source object itself is created by the revision coordinator, in the committed
            // transaction that also stages the revision — publication runs in its own and has to
            // be able to read both.
            materializeRevision(ctx, content, UUID.randomUUID(), plan, null);
            audit(ctx, "CONNECTOR_MATERIALIZE", content.externalObjectId(), "OBJECT_MATERIALIZED");
            return ObjectOutcome.MATERIALIZED;
        }
        ConnectorHeadView current = head.get();
        if (content.contentRevision().equals(current.currentContentRevision())) {
            return rotate(ctx, current, plan, content.externalObjectId());
        }
        return rematerialize(ctx, current, content, plan);
    }

    /**
     * Materializes a changed content revision onto the object that already exists. Registering
     * the new revision appends the sealed ACL generation and advances the head to it in one
     * step, so the edited text and the permissions that came with it commit together. The new
     * source revision becomes current, and because retrieval only serves chunks belonging to
     * the current revision, the superseded text leaves the index the moment this commits.
     *
     * <p>A retired object is not revived here. Whether content reappearing at the source should
     * resurrect a tombstoned object, and under whose ACL, is a decision this path should not
     * make silently, so the revision coordinator refuses it as an isolated per-object error.
     */
    private ObjectOutcome rematerialize(
            ConnectorIngestionContext ctx,
            ConnectorHeadView current,
            ConnectorContentItem content,
            AclPlan plan) {
        SourceObject source = sources
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        ctx.organizationId(),
                        ctx.profile().sourceSystem(),
                        ctx.sourceConnectionKey(),
                        content.externalObjectId())
                .orElseThrow(() -> new IllegalStateException(
                        "an ACL head exists without its source object: " + content.externalObjectId()));
        materializeRevision(ctx, content, source.getId(), plan, current.currentSnapshotId());
        audit(ctx, "CONNECTOR_REMATERIALIZE", content.externalObjectId(), "CONTENT_REVISION_MATERIALIZED");
        return ObjectOutcome.REMATERIALIZED;
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

    /**
     * The connection's active objects that a complete crawl did not mention — the ones deleted
     * at the source since the last crawl. The caller retires them; this only reports the diff,
     * and only its caller knows whether the crawl was complete enough to be trusted with it.
     */
    List<String> vanishedSince(ConnectorIngestionContext ctx, Set<String> crawledObjectIds) {
        return sources
                .findActiveExternalObjectIds(
                        ctx.organizationId(), ctx.profile().sourceSystem(), ctx.sourceConnectionKey())
                .stream()
                .filter(externalObjectId -> !crawledObjectIds.contains(externalObjectId))
                .toList();
    }

    /** Retires a tombstoned object from retrieval. Returns whether an active object was retired. */
    boolean retire(ConnectorIngestionContext ctx, ConnectorTombstone tombstone) {
        var existing = sources.findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                ctx.organizationId(),
                ctx.profile().sourceSystem(),
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

    /**
     * Registers the crawled revision, normalizes it, and materializes it as the current
     * revision of {@code source}. The same path serves a first crawl and a later edit; the only
     * difference is whether an ACL head already exists to compare against, which the caller
     * supplies as {@code expectedCurrentSnapshotId}.
     */
    private void materializeRevision(
            ConnectorIngestionContext ctx,
            ConnectorContentItem content,
            UUID sourceId,
            AclPlan plan,
            UUID expectedCurrentSnapshotId) {
        RawSourceRef raw = ingestion.registerConnectorSource(
                registerCommand(
                        ctx, content, plan.entries(), Instant.now().plus(ACL_TTL), expectedCurrentSnapshotId),
                plan.membership());
        NormalizedRecordRef normalized = ingestion.normalize(new NormalizeRawSourceCommand(
                ctx.organizationId(),
                raw.rawSourceObjectId(),
                NORMALIZER_VERSION,
                content.title(),
                content.body(),
                "und"));
        byte[] bytes = content.body().getBytes(StandardCharsets.UTF_8);
        String contentSha256 = sha256(content.body());
        ObjectKey key = new ObjectKey("organizations/" + ctx.organizationId()
                + "/connector/" + ctx.sourceSystem()
                + "/" + ctx.sourceConnectionKey()
                + "/" + content.externalObjectId()
                + "/" + content.contentRevision());

        // A retry that already stored this exact text reuses the revision it made rather than
        // storing a second copy of it under a new ordinal.
        ConnectorRevisionDraft draft = revisionCoordinator
                .findExisting(ctx, content, contentSha256)
                .orElse(null);
        if (draft == null) {
            UUID revisionId = UUID.randomUUID();
            StoredObject stored = objects.put(
                    new ObjectWriteRequest(
                            key,
                            bytes.length,
                            ctx.profile().mediaType(),
                            Map.of(
                                    "organization-id", ctx.organizationId().toString(),
                                    "source-object-id", sourceId.toString(),
                                    "source-revision-id", revisionId.toString())),
                    new ByteArrayInputStream(bytes));
            try {
                draft = revisionCoordinator.stage(
                        ctx, content, sourceId, revisionId, UUID.randomUUID(), stored);
            } catch (RuntimeException failure) {
                try {
                    objects.delete(key);
                } catch (ObjectStorageException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        try {
            List<String> texts = chunk(content.body());
            ConnectorEmbeddingResult embedding = requireEmbedder().embed(ctx.organizationId(), texts);
            List<float[]> vectors = embedding.vectors();
            if (vectors.size() != texts.size()) {
                throw new IllegalStateException("connector embedding count did not match the chunk count");
            }
            List<KnowledgeChunkDraft> drafts = new ArrayList<>(texts.size());
            for (int index = 0; index < texts.size(); index++) {
                drafts.add(new KnowledgeChunkDraft(
                        index, texts.get(index), sha256(texts.get(index)), null, null, null, null, vectors.get(index)));
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
                    drafts));
            revisionCoordinator.complete(
                    draft,
                    PIPELINE_VERSION,
                    PARSER_VERSION,
                    CHUNKER_VERSION,
                    embedding.profile(),
                    raw,
                    normalized,
                    asset);
        } catch (RuntimeException failure) {
            try {
                objects.delete(key);
            } catch (ObjectStorageException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
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
                ctx.profile().objectType(),
                content.title(),
                content.body(),
                null,
                null,
                ctx.profile().classification(),
                ctx.profile().declaredAccess(),
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

    private static List<String> chunk(String body) {
        List<String> chunks = new ArrayList<>();
        for (String paragraph : body.strip().split("\\n\\s*\\n")) {
            String trimmed = paragraph.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            for (int start = 0; start < trimmed.length(); start += MAX_CHUNK_CHARS) {
                chunks.add(trimmed.substring(start, Math.min(trimmed.length(), start + MAX_CHUNK_CHARS)));
                if (chunks.size() >= MAX_CHUNKS) {
                    return List.copyOf(chunks);
                }
            }
        }
        if (chunks.isEmpty()) {
            chunks.add(body.strip());
        }
        return List.copyOf(chunks);
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
        ROTATED,
        REMATERIALIZED
    }
}
