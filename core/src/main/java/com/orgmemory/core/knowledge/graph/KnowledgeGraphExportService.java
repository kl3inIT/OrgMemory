package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.knowledge.retrieval.GraphEvidenceVerifier;
import com.orgmemory.core.knowledge.asset.KnowledgeProjectionNamespaces;
import com.orgmemory.core.knowledge.retrieval.KnowledgeRetrievalUnavailableException;
import com.orgmemory.core.knowledge.retrieval.VerifiedGraphEvidenceScope;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceQuery;
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

    private final KnowledgeSpaceQuery spaces;
    private final RelationshipAuthorizationPort authorization;
    private final GraphEvidenceVerifier evidenceVerifier;
    private final GraphExportReader reader;
    private final GraphExportFormatter formatter = new GraphExportFormatter();
    private final PermissionAuditService audit;

    KnowledgeGraphExportService(
            KnowledgeSpaceQuery spaces,
            RelationshipAuthorizationPort authorization,
            GraphEvidenceVerifier evidenceVerifier,
            GraphExportReader reader,
            PermissionAuditService audit) {
        this.spaces = spaces;
        this.authorization = authorization;
        this.evidenceVerifier = evidenceVerifier;
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
        if (!spaces.isActive(actor.organizationId(), knowledgeSpaceId)) {
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
        VerifiedGraphEvidenceScope resolved;
        try {
            resolved = evidenceVerifier.verifyScope(
                    actor, entry.policyVersion());
        } catch (KnowledgeRetrievalUnavailableException unavailable) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Knowledge graph permissions changed while preparing the export",
                    unavailable);
        }
        ProjectionNamespace namespace = KnowledgeProjectionNamespaces.forSpace(
                actor.organizationId(), knowledgeSpaceId);
        var document = reader.read(
                resolved.forKnowledgeSpace(knowledgeSpaceId),
                namespace);
        VerifiedGraphEvidenceScope current;
        try {
            current = evidenceVerifier.verifyScope(
                    actor, entry.policyVersion());
        } catch (KnowledgeRetrievalUnavailableException unavailable) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Knowledge graph permissions changed while preparing the export",
                    unavailable);
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
            VerifiedGraphEvidenceScope first,
            VerifiedGraphEvidenceScope second,
            UUID knowledgeSpaceId) {
        return first.authorizationModelId().equals(second.authorizationModelId())
                && first.hasSameAssetsAndGeneration(second, knowledgeSpaceId);
    }

    private static OrgMemoryAccessDeniedException accessDenied() {
        return new OrgMemoryAccessDeniedException(
                "The current user is not authorized to export this graph");
    }
}
