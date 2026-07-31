package com.orgmemory.core.knowledge;

import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.graphrag.export.GraphExportFormat;
import com.orgmemory.graphrag.export.GraphExportFormatter;
import com.orgmemory.graphrag.export.GraphExportReader;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permission-aware bulk graph egress with the same evidence scope as retrieval. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class KnowledgeGraphExportService {

    private static final PermissionKey CAN_EXPORT_GRAPH =
            PermissionKey.of("can_export_graph");

    private final KnowledgeSpaceRepository spaces;
    private final RelationshipAuthorizationPort authorization;
    private final KnowledgeEvidenceScopeResolver evidenceScopes;
    private final GraphExportReader reader;
    private final GraphExportFormatter formatter = new GraphExportFormatter();
    private final PermissionAuditService audit;

    KnowledgeGraphExportService(
            KnowledgeSpaceRepository spaces,
            RelationshipAuthorizationPort authorization,
            KnowledgeEvidenceScopeResolver evidenceScopes,
            GraphExportReader reader,
            PermissionAuditService audit) {
        this.spaces = spaces;
        this.authorization = authorization;
        this.evidenceScopes = evidenceScopes;
        this.reader = reader;
        this.audit = audit;
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
        var entry = authorization.check(new RelationshipAuthorizationQuery(
                        actor.principal(),
                        CAN_EXPORT_GRAPH,
                        ResourceRef.of(
                                actor.organizationId(),
                                "knowledge_space",
                                knowledgeSpaceId)));
        if (!entry.allowed()) {
            throw accessDenied();
        }
        ResolvedKnowledgeEvidenceScope resolved;
        try {
            resolved = evidenceScopes.resolve(actor, entry.policyVersion());
        } catch (KnowledgeEvidenceScopeUnavailableException unavailable) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Knowledge graph permissions changed while preparing the export",
                    unavailable);
        }
        ProjectionNamespace namespace = KnowledgeProjectionNamespaces.forSpace(
                actor.organizationId(), knowledgeSpaceId);
        var document = reader.read(
                resolved.forKnowledgeSpace(knowledgeSpaceId),
                namespace);
        ResolvedKnowledgeEvidenceScope current;
        try {
            current = evidenceScopes.resolve(actor, entry.policyVersion());
        } catch (KnowledgeEvidenceScopeUnavailableException unavailable) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Knowledge graph permissions changed while preparing the export");
        }
        if (!sameSpaceScope(resolved, current, knowledgeSpaceId)) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Knowledge graph permissions changed while preparing the export");
        }
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

    private static boolean sameSpaceScope(
            ResolvedKnowledgeEvidenceScope first,
            ResolvedKnowledgeEvidenceScope second,
            UUID knowledgeSpaceId) {
        return first.authorizationModelId().equals(second.authorizationModelId())
                && first.forKnowledgeSpace(knowledgeSpaceId)
                        .authorizedAssetIds()
                        .equals(second.forKnowledgeSpace(knowledgeSpaceId)
                                .authorizedAssetIds())
                && first.aclGenerationByKnowledgeSpace()
                        .getOrDefault(knowledgeSpaceId, 0L)
                        .equals(second.aclGenerationByKnowledgeSpace()
                                .getOrDefault(knowledgeSpaceId, 0L));
    }

    private static OrgMemoryAccessDeniedException accessDenied() {
        return new OrgMemoryAccessDeniedException(
                "The current user is not authorized to export this graph");
    }
}
