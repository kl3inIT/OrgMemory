package com.orgmemory.core.knowledge;

import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.graphrag.export.GraphExportDocument;
import com.orgmemory.graphrag.export.GraphExportReader;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Permission-scoped, bounded graph read model for the product UI. */
public class KnowledgeGraphExplorerService {

    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");

    private final KnowledgeSpaceRepository spaces;
    private final RelationshipAuthorizationPort authorization;
    private final KnowledgeEvidenceScopeResolver evidenceScopes;
    private final GraphExportReader graphs;
    private final GraphExplorerProperties properties;
    private final PermissionAuditService audit;

    public KnowledgeGraphExplorerService(
            KnowledgeSpaceRepository spaces,
            RelationshipAuthorizationPort authorization,
            KnowledgeEvidenceScopeResolver evidenceScopes,
            GraphExportReader graphs,
            GraphExplorerProperties properties,
            PermissionAuditService audit) {
        this.spaces = spaces;
        this.authorization = authorization;
        this.evidenceScopes = evidenceScopes;
        this.graphs = graphs;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public KnowledgeGraphView explore(
            CurrentActor actor,
            UUID knowledgeSpaceId,
            String query,
            Integer requestedEntityLimit,
            String requestId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        String normalizedQuery = normalizeQuery(query);
        int entityLimit = validateLimit(requestedEntityLimit);
        String normalizedRequestId = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId.strip();
        String policyVersion = requireSpaceAccess(actor, knowledgeSpaceId);
        return explore(
                actor,
                knowledgeSpaceId,
                normalizedQuery,
                entityLimit,
                normalizedRequestId,
                policyVersion,
                0);
    }

    private KnowledgeGraphView explore(
            CurrentActor actor,
            UUID knowledgeSpaceId,
            String query,
            int entityLimit,
            String requestId,
            String policyVersion,
            int attempt) {
        ResolvedKnowledgeEvidenceScope initial =
                resolve(actor, policyVersion);
        if (!initial.knowledgeSpaceIds().contains(knowledgeSpaceId)) {
            return empty(
                    actor,
                    knowledgeSpaceId,
                    requestId,
                    policyVersion,
                    "NO_AUTHORIZED_GRAPH_EVIDENCE");
        }
        ProjectionNamespace namespace = new ProjectionNamespace(
                actor.organizationId(),
                "default",
                knowledgeSpaceId.toString());
        GraphExportDocument document = graphs.read(
                initial.forKnowledgeSpace(knowledgeSpaceId),
                namespace);

        ResolvedKnowledgeEvidenceScope current =
                resolve(actor, policyVersion);
        if (!sameSpaceScope(initial, current, knowledgeSpaceId)) {
            if (attempt == 0) {
                return explore(
                        actor,
                        knowledgeSpaceId,
                        query,
                        entityLimit,
                        requestId,
                        policyVersion,
                        1);
            }
            throw new KnowledgeRetrievalUnavailableException(
                    "Knowledge graph authorization changed during the request");
        }

        KnowledgeGraphView view = project(
                knowledgeSpaceId,
                document,
                query,
                entityLimit,
                properties.maximumRelationLimit());
        audit.record(new PermissionAuditCommand(
                actor.organizationId(),
                actor.userId(),
                "EXPLORE_KNOWLEDGE_GRAPH",
                "knowledge_space",
                knowledgeSpaceId.toString(),
                PermissionAuditDecision.ALLOW,
                "AUTHORIZED_GRAPH_EXPLORER",
                policyVersion,
                requestId,
                query));
        return view;
    }

    private String requireSpaceAccess(
            CurrentActor actor,
            UUID knowledgeSpaceId) {
        if (!spaces.existsByIdAndOrganizationIdAndActiveTrue(
                knowledgeSpaceId,
                actor.organizationId())) {
            throw accessDenied();
        }
        var decision = authorization.check(
                new RelationshipAuthorizationQuery(
                        actor.principal(),
                        CAN_VIEW,
                        ResourceRef.of(
                                actor.organizationId(),
                                "knowledge_space",
                                knowledgeSpaceId)));
        if (!decision.allowed()) {
            throw accessDenied();
        }
        return decision.policyVersion();
    }

    private ResolvedKnowledgeEvidenceScope resolve(
            CurrentActor actor,
            String policyVersion) {
        try {
            return evidenceScopes.resolve(actor, policyVersion);
        } catch (KnowledgeEvidenceScopeUnavailableException unavailable) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Knowledge graph permissions are temporarily unavailable");
        }
    }

    private KnowledgeGraphView empty(
            CurrentActor actor,
            UUID knowledgeSpaceId,
            String requestId,
            String policyVersion,
            String reason) {
        audit.record(new PermissionAuditCommand(
                actor.organizationId(),
                actor.userId(),
                "EXPLORE_KNOWLEDGE_GRAPH",
                "knowledge_space",
                knowledgeSpaceId.toString(),
                PermissionAuditDecision.ALLOW,
                reason,
                policyVersion,
                requestId,
                null));
        return new KnowledgeGraphView(
                knowledgeSpaceId,
                List.of(),
                List.of(),
                false);
    }

    private KnowledgeGraphView project(
            UUID knowledgeSpaceId,
            GraphExportDocument document,
            String query,
            int entityLimit,
            int relationLimit) {
        String needle = query.toLowerCase(Locale.ROOT);
        Set<UUID> relationMatchedEntities = new LinkedHashSet<>();
        if (!needle.isEmpty()) {
            document.relations().stream()
                    .filter(relation -> matches(relation, needle))
                    .forEach(relation -> {
                        relationMatchedEntities.add(
                                relation.sourceEntityId());
                        relationMatchedEntities.add(
                                relation.targetEntityId());
                    });
        }
        List<GraphExportDocument.EntityRow> rankedEntities =
                document.entities().stream()
                        .filter(entity -> needle.isEmpty()
                                || relationMatchedEntities.contains(entity.id())
                                || matches(entity, needle))
                        .sorted(Comparator
                                .comparingInt((GraphExportDocument.EntityRow row) ->
                                        row.evidence().size())
                                .reversed()
                                .thenComparing(
                                        GraphExportDocument.EntityRow::name)
                                .thenComparing(
                                        GraphExportDocument.EntityRow::id))
                        .toList();
        List<GraphExportDocument.EntityRow> selectedEntities =
                rankedEntities.stream().limit(entityLimit).toList();
        Set<UUID> selectedIds = selectedEntities.stream()
                .map(GraphExportDocument.EntityRow::id)
                .collect(
                        java.util.stream.Collectors.toCollection(
                                LinkedHashSet::new));
        List<GraphExportDocument.RelationRow> rankedRelations =
                document.relations().stream()
                        .filter(relation -> selectedIds.contains(
                                        relation.sourceEntityId())
                                && selectedIds.contains(
                                        relation.targetEntityId()))
                        .filter(relation -> needle.isEmpty()
                                || matches(relation, needle)
                                || relationMatchedEntities.contains(
                                        relation.sourceEntityId())
                                || relationMatchedEntities.contains(
                                        relation.targetEntityId()))
                        .sorted(Comparator
                                .comparingDouble(
                                        GraphExportDocument.RelationRow::weight)
                                .reversed()
                                .thenComparing(
                                        GraphExportDocument.RelationRow::id))
                        .toList();
        List<GraphExportDocument.RelationRow> selectedRelations =
                rankedRelations.stream().limit(relationLimit).toList();
        boolean truncated = rankedEntities.size() > selectedEntities.size()
                || rankedRelations.size() > selectedRelations.size();
        return new KnowledgeGraphView(
                knowledgeSpaceId,
                selectedEntities.stream()
                        .map(KnowledgeGraphExplorerService::entity)
                        .toList(),
                selectedRelations.stream()
                        .map(KnowledgeGraphExplorerService::relation)
                        .toList(),
                truncated);
    }

    private static KnowledgeGraphView.Entity entity(
            GraphExportDocument.EntityRow row) {
        return new KnowledgeGraphView.Entity(
                row.id(),
                row.name(),
                row.type(),
                row.description(),
                citationIds(row.evidence()));
    }

    private static KnowledgeGraphView.Relation relation(
            GraphExportDocument.RelationRow row) {
        return new KnowledgeGraphView.Relation(
                row.id(),
                row.sourceEntityId(),
                row.targetEntityId(),
                row.type(),
                row.description(),
                row.weight(),
                citationIds(row.evidence()));
    }

    private static List<UUID> citationIds(
            List<EvidenceReference> evidence) {
        return evidence.stream()
                .map(EvidenceReference::chunkId)
                .distinct()
                .sorted()
                .toList();
    }

    private static boolean matches(
            GraphExportDocument.EntityRow row,
            String needle) {
        return contains(row.name(), needle)
                || contains(row.type(), needle)
                || contains(row.description(), needle);
    }

    private static boolean matches(
            GraphExportDocument.RelationRow row,
            String needle) {
        return contains(row.type(), needle)
                || contains(row.description(), needle)
                || row.keywords().stream()
                        .anyMatch(value -> contains(value, needle));
    }

    private static boolean contains(String value, String needle) {
        return value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean sameSpaceScope(
            ResolvedKnowledgeEvidenceScope initial,
            ResolvedKnowledgeEvidenceScope current,
            UUID knowledgeSpaceId) {
        return initial.authorizationModelId()
                        .equals(current.authorizationModelId())
                && initial.forKnowledgeSpace(knowledgeSpaceId)
                        .authorizationFingerprint()
                        .equals(current.forKnowledgeSpace(knowledgeSpaceId)
                                .authorizationFingerprint());
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String normalized = query.strip();
        if (normalized.length() > properties.maximumQueryLength()) {
            throw new IllegalArgumentException(
                    "q must not exceed "
                            + properties.maximumQueryLength()
                            + " characters");
        }
        return normalized;
    }

    private int validateLimit(Integer requested) {
        int value = requested == null
                ? properties.defaultEntityLimit()
                : requested;
        if (value < 1 || value > properties.maximumEntityLimit()) {
            throw new IllegalArgumentException(
                    "entityLimit must be between 1 and "
                            + properties.maximumEntityLimit());
        }
        return value;
    }

    private static OrgMemoryAccessDeniedException accessDenied() {
        return new OrgMemoryAccessDeniedException(
                "The current user is not authorized to view this graph");
    }
}
