package com.orgmemory.core.knowledge;

import com.orgmemory.core.authorization.AuthorizedResourceQuery;
import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.organization.UserRole;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.export.GraphExportFormat;
import com.orgmemory.graphrag.export.GraphExportFormatter;
import com.orgmemory.graphrag.export.GraphExportReader;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permission-aware bulk graph egress with the same evidence scope as retrieval. */
@Service
public class KnowledgeGraphExportService {

    private static final PermissionKey CAN_EXPORT_GRAPH =
            PermissionKey.of("can_export_graph");
    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");

    private final KnowledgeSpaceRepository spaces;
    private final KnowledgeAssetRepository assets;
    private final SourceAclSnapshotRepository aclSnapshots;
    private final AppUserRepository users;
    private final RelationshipAuthorizationPort authorization;
    private final RelationshipAuthorizationSetPort authorizationSets;
    private final GraphExportReader reader;
    private final GraphExportFormatter formatter = new GraphExportFormatter();
    private final PermissionAuditService audit;
    private final KnowledgeRetrievalProperties retrievalProperties;

    KnowledgeGraphExportService(
            KnowledgeSpaceRepository spaces,
            KnowledgeAssetRepository assets,
            SourceAclSnapshotRepository aclSnapshots,
            AppUserRepository users,
            RelationshipAuthorizationPort authorization,
            RelationshipAuthorizationSetPort authorizationSets,
            GraphExportReader reader,
            PermissionAuditService audit,
            KnowledgeRetrievalProperties retrievalProperties) {
        this.spaces = spaces;
        this.assets = assets;
        this.aclSnapshots = aclSnapshots;
        this.users = users;
        this.authorization = authorization;
        this.authorizationSets = authorizationSets;
        this.reader = reader;
        this.audit = audit;
        this.retrievalProperties = retrievalProperties;
    }

    @Transactional(readOnly = true)
    public GraphExportFormatter.Artifact export(
            CurrentActor actor,
            UUID knowledgeSpaceId,
            GraphExportFormat format,
            String requestId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        Objects.requireNonNull(format, "format");
        if (!spaces.existsByIdAndOrganizationIdAndActiveTrue(
                knowledgeSpaceId, actor.organizationId())) {
            throw accessDenied();
        }
        AppUser subject = users.findById(actor.userId())
                .filter(candidate ->
                        candidate.getOrganizationId().equals(actor.organizationId()))
                .filter(AppUser::isActive)
                .filter(candidate -> candidate.getRole() != UserRole.ADMIN)
                .orElseThrow(KnowledgeGraphExportService::accessDenied);
        AuthorizationDecision entry =
                authorization.check(new RelationshipAuthorizationQuery(
                        actor.principal(),
                        CAN_EXPORT_GRAPH,
                        ResourceRef.of(
                                actor.organizationId(),
                                "knowledge_space",
                                knowledgeSpaceId)));
        if (!entry.allowed()) {
            throw accessDenied();
        }
        var listed = authorizationSets.listAuthorizedResources(
                new AuthorizedResourceQuery(
                        actor.organizationId(),
                        actor.principal(),
                        CAN_VIEW,
                        "knowledge_asset"));
        if (!listed.resolved()
                || !entry.policyVersion().equals(listed.policyVersion())) {
            throw new IllegalStateException(
                    "authorization model changed while preparing graph export");
        }
        List<ResourceRef> resources = listed.resources().stream()
                .filter(resource -> actor.organizationId()
                                .equals(resource.organizationId())
                        && "knowledge_asset".equals(resource.type()))
                .distinct()
                .toList();
        if (resources.size() != listed.resources().size()
                || resources.size()
                        > retrievalProperties.maximumAuthorizedObjects()) {
            throw new IllegalStateException(
                    "authorization returned an invalid Knowledge Asset set");
        }
        List<UUID> listedIds;
        try {
            listedIds = resources.stream()
                    .map(resource -> UUID.fromString(resource.id()))
                    .toList();
        } catch (IllegalArgumentException invalidIdentifier) {
            throw new IllegalStateException(
                    "authorization returned an invalid Knowledge Asset id",
                    invalidIdentifier);
        }
        Set<UUID> authorizedIds = listedIds.isEmpty()
                ? Set.of()
                : Set.copyOf(assets.findActiveIdsInKnowledgeSpace(
                        actor.organizationId(), knowledgeSpaceId, listedIds));
        long aclGeneration = authorizedIds.isEmpty()
                ? 0
                : aclSnapshots.maximumCurrentAclGeneration(
                        actor.organizationId(), authorizedIds);
        ProjectionNamespace namespace = new ProjectionNamespace(
                actor.organizationId(),
                "default",
                knowledgeSpaceId.toString());
        var document = reader.read(
                new AuthorizedEvidenceScope(
                        actor.organizationId(),
                        actor.userId(),
                        subject.getDepartmentId(),
                        subject.getRole() == UserRole.EXECUTIVE,
                        authorizedIds,
                        entry.policyVersion(),
                        aclGeneration,
                        Instant.now()),
                namespace);
        GraphExportFormatter.Artifact artifact =
                formatter.format(document, format);
        audit.record(new PermissionAuditCommand(
                actor.organizationId(),
                actor.userId(),
                "EXPORT_KNOWLEDGE_GRAPH",
                "knowledge_space",
                knowledgeSpaceId.toString(),
                PermissionAuditDecision.ALLOW,
                "AUTHORIZED_GRAPH_EXPORT",
                entry.policyVersion(),
                requestId,
                null));
        return artifact;
    }

    private static OrgMemoryAccessDeniedException accessDenied() {
        return new OrgMemoryAccessDeniedException(
                "The current user is not authorized to export this graph");
    }
}
