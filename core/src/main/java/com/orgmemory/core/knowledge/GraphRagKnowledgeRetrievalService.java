package com.orgmemory.core.knowledge;

import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.query.LightRagQueryEngine;
import com.orgmemory.graphrag.query.LightRagQueryRequest;
import com.orgmemory.graphrag.query.LightRagQueryResult;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permission-aware application shell around the framework-neutral LightRAG
 * engine. OpenFGA ListObjects establishes the scope; BatchCheck and the
 * canonical ledger recheck it before any selected evidence reaches the model.
 */
public class GraphRagKnowledgeRetrievalService
        implements PermissionAwareKnowledgeSearch {

    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final String RESOURCE_TYPE = "knowledge_asset";
    private static final int MAX_REQUEST_ID_LENGTH = 128;

    private final KnowledgeSearchAuthorizationService searchAuthorization;
    private final KnowledgeEvidenceScopeResolver evidenceScopes;
    private final RelationshipAuthorizationSetPort authorization;
    private final SecureKnowledgeRetrievalStore canonicalEvidence;
    private final EmbeddingProfileRegistry embeddingProfiles;
    private final KnowledgeEmbeddingProperties embedding;
    private final ProjectionPublicationStore publications;
    private final LightRagQueryEngine engine;
    private final GraphRagRetrievalPolicy policy;
    private final PermissionAuditService audit;
    private final KnowledgeRetrievalProperties retrievalProperties;

    public GraphRagKnowledgeRetrievalService(
            KnowledgeSearchAuthorizationService searchAuthorization,
            KnowledgeEvidenceScopeResolver evidenceScopes,
            RelationshipAuthorizationSetPort authorization,
            SecureKnowledgeRetrievalStore canonicalEvidence,
            EmbeddingProfileRegistry embeddingProfiles,
            KnowledgeEmbeddingProperties embedding,
            ProjectionPublicationStore publications,
            LightRagQueryEngine engine,
            GraphRagRetrievalPolicy policy,
            PermissionAuditService audit,
            KnowledgeRetrievalProperties retrievalProperties) {
        this.searchAuthorization = searchAuthorization;
        this.evidenceScopes = evidenceScopes;
        this.authorization = authorization;
        this.canonicalEvidence = canonicalEvidence;
        this.embeddingProfiles = embeddingProfiles;
        this.embedding = embedding;
        this.publications = publications;
        this.engine = engine;
        this.policy = policy;
        this.audit = audit;
        this.retrievalProperties = retrievalProperties;
    }

    @Transactional(readOnly = true)
    @Override
    public SecureKnowledgeSearchResult search(
            CurrentActor actor,
            String query,
            Integer requestedLimit,
            String suppliedRequestId) {
        String requestId = requestId(suppliedRequestId);
        String normalizedQuery = normalizeQuery(query);
        int limit = validateLimit(requestedLimit);
        String authorizationModelId = searchAuthorization.require(
                actor,
                requestId,
                normalizedQuery);
        return search(
                actor,
                normalizedQuery,
                limit,
                requestId,
                authorizationModelId,
                0);
    }

    private SecureKnowledgeSearchResult search(
            CurrentActor actor,
            String query,
            int limit,
            String requestId,
            String authorizationModelId,
            int attempt) {
        ResolvedKnowledgeEvidenceScope initial =
                resolve(actor, authorizationModelId, requestId, query);
        if (initial.allAssetIds().isEmpty()) {
            audit.record(searchAuthorization.command(
                    actor,
                    requestId,
                    query,
                    PermissionAuditDecision.ALLOW,
                    "NO_AUTHORIZED_KNOWLEDGE_ASSETS",
                    initial.authorizationModelId()));
            return new SecureKnowledgeSearchResult(requestId, List.of());
        }

        EmbeddingProfileRef profile = embeddingProfiles
                .find(
                        actor.organizationId(),
                        new EmbeddingProfileSpec(
                                embedding.provider(),
                                embedding.model(),
                                embedding.dimensions(),
                                EmbeddingDistanceMetric.COSINE))
                .orElseThrow(() -> searchAuthorization.unavailable(
                        actor,
                        requestId,
                        query,
                        "EMBEDDING_PROFILE_NOT_INDEXED",
                        initial.authorizationModelId()));

        List<SpaceReference> candidates =
                queryPublishedSpaces(initial, profile, query, limit);
        if (candidates.isEmpty()) {
            audit.record(searchAuthorization.command(
                    actor,
                    requestId,
                    query,
                    PermissionAuditDecision.ALLOW,
                    "NO_ELIGIBLE_EVIDENCE",
                    initial.authorizationModelId()));
            return new SecureKnowledgeSearchResult(requestId, List.of());
        }

        ResolvedKnowledgeEvidenceScope current =
                resolve(actor, authorizationModelId, requestId, query);
        if (!sameAuthorizationScope(initial, current)) {
            return retryOrFail(
                    actor,
                    query,
                    limit,
                    requestId,
                    authorizationModelId,
                    attempt,
                    "AUTHORIZATION_SCOPE_CHANGED");
        }

        List<SpaceReference> selected = candidates.stream()
                .sorted(Comparator.comparingDouble(SpaceReference::score)
                        .reversed()
                        .thenComparing(candidate ->
                                candidate.reference().evidence().chunkId()))
                .filter(distinctByChunk())
                .limit(limit)
                .toList();
        verifyOpenFga(
                actor,
                query,
                requestId,
                current.authorizationModelId(),
                selected);
        List<SecureRetrievalCandidate> verified =
                recheckCanonical(current, selected);
        if (!sameEvidence(selected, verified)) {
            return retryOrFail(
                    actor,
                    query,
                    limit,
                    requestId,
                    authorizationModelId,
                    attempt,
                    "CANONICAL_EVIDENCE_CHANGED");
        }

        Map<UUID, SecureRetrievalCandidate> canonicalByChunk = verified.stream()
                .collect(Collectors.toMap(
                        SecureRetrievalCandidate::chunkId,
                        Function.identity()));
        List<RetrievedKnowledgeEvidence> evidence = new ArrayList<>();
        List<PermissionAuditCommand> auditCommands = new ArrayList<>();
        auditCommands.add(searchAuthorization.command(
                actor,
                requestId,
                query,
                PermissionAuditDecision.ALLOW,
                "SECURE_GRAPH_RAG_RETRIEVAL_APPLIED",
                current.authorizationModelId()));
        for (SpaceReference selectedReference : selected) {
            SecureRetrievalCandidate canonical = canonicalByChunk.get(
                    selectedReference.reference().evidence().chunkId());
            auditCommands.add(evidenceAudit(
                    actor,
                    requestId,
                    query,
                    canonical,
                    current.authorizationModelId()));
            evidence.add(toEvidence(canonical, selectedReference.score()));
        }
        audit.recordAll(auditCommands);
        return new SecureKnowledgeSearchResult(requestId, evidence);
    }

    private List<SpaceReference> queryPublishedSpaces(
            ResolvedKnowledgeEvidenceScope scope,
            EmbeddingProfileRef profile,
            String query,
            int limit) {
        if (scope.knowledgeSpaceIds().size()
                > policy.maximumKnowledgeSpaces()) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Secure knowledge retrieval is temporarily unavailable");
        }
        List<SpaceReference> references = new ArrayList<>();
        for (UUID knowledgeSpaceId :
                scope.knowledgeSpaceIds().stream().sorted().toList()) {
            var evidenceScope = scope.forKnowledgeSpace(knowledgeSpaceId);
            ProjectionNamespace namespace = namespace(
                    scope.organizationId(),
                    knowledgeSpaceId);
            var snapshot = publications.current(namespace);
            if (snapshot.isEmpty()) {
                continue;
            }
            LightRagQueryResult result = engine.execute(
                    new LightRagQueryRequest(
                            evidenceScope,
                            snapshot.orElseThrow(),
                            query,
                            policy.contextOptions(limit),
                            profile.id(),
                            profile.dimensions(),
                            null,
                            List.of()));
            Map<UUID, Double> scoreByChunk = result.trace()
                    .chunkSignals()
                    .stream()
                    .collect(Collectors.toMap(
                            LightRagQueryResult.ChunkSignal::chunkId,
                            signal -> signal.rerankScore() == null
                                    ? signal.retrievalScore()
                                    : signal.rerankScore(),
                            Math::max));
            result.references().forEach(reference -> references.add(
                    new SpaceReference(
                            knowledgeSpaceId,
                            reference,
                            scoreByChunk.getOrDefault(
                                    reference.evidence().chunkId(),
                                    0.0))));
        }
        return List.copyOf(references);
    }

    private ResolvedKnowledgeEvidenceScope resolve(
            CurrentActor actor,
            String authorizationModelId,
            String requestId,
            String query) {
        try {
            return evidenceScopes.resolve(actor, authorizationModelId);
        } catch (KnowledgeEvidenceScopeUnavailableException unavailable) {
            throw searchAuthorization.unavailable(
                    actor,
                    requestId,
                    query,
                    unavailable.reasonCode(),
                    unavailable.policyVersion());
        }
    }

    private void verifyOpenFga(
            CurrentActor actor,
            String query,
            String requestId,
            String authorizationModelId,
            List<SpaceReference> selected) {
        List<ResourceRef> resources = selected.stream()
                .map(candidate -> ResourceRef.of(
                        actor.organizationId(),
                        RESOURCE_TYPE,
                        candidate.reference()
                                .evidence()
                                .knowledgeAssetId()))
                .distinct()
                .toList();
        var checked = authorization.batchCheck(
                new BatchAuthorizationQuery(
                        actor.organizationId(),
                        actor.principal(),
                        CAN_VIEW,
                        resources));
        if (!checked.resolved()
                || !authorizationModelId.equals(checked.policyVersion())
                || checked.decisions().size() != resources.size()) {
            throw searchAuthorization.unavailable(
                    actor,
                    requestId,
                    query,
                    checked.reasonCode(),
                    checked.policyVersion());
        }
        for (ResourceRef resource : resources) {
            var decision = checked.decisions().get(resource);
            if (decision == null
                    || !decision.allowed()
                    || !authorizationModelId.equals(
                            decision.policyVersion())) {
                throw searchAuthorization.unavailable(
                        actor,
                        requestId,
                        query,
                        "FINAL_OPENFGA_RECHECK_DENIED",
                        checked.policyVersion());
            }
        }
    }

    private List<SecureRetrievalCandidate> recheckCanonical(
            ResolvedKnowledgeEvidenceScope scope,
            List<SpaceReference> selected) {
        return canonicalEvidence.recheck(
                new SecureKnowledgeRetrievalStore.RetrievalScope(
                        scope.organizationId(),
                        scope.actorUserId(),
                        scope.actorDepartmentId(),
                        scope.actorExecutive(),
                        scope.allAssetIds().stream().sorted().toList(),
                        scope.authorizationModelId(),
                        scope.evaluatedAt()),
                selected.stream()
                        .map(candidate -> candidate.reference()
                                .evidence()
                                .chunkId())
                        .toList());
    }

    private SecureKnowledgeSearchResult retryOrFail(
            CurrentActor actor,
            String query,
            int limit,
            String requestId,
            String authorizationModelId,
            int attempt,
            String reason) {
        if (attempt == 0) {
            return search(
                    actor,
                    query,
                    limit,
                    requestId,
                    authorizationModelId,
                    1);
        }
        throw searchAuthorization.unavailable(
                actor,
                requestId,
                query,
                reason,
                authorizationModelId);
    }

    private static boolean sameAuthorizationScope(
            ResolvedKnowledgeEvidenceScope left,
            ResolvedKnowledgeEvidenceScope right) {
        if (!left.authorizationModelId()
                .equals(right.authorizationModelId())) {
            return false;
        }
        Set<UUID> spaces = new LinkedHashSet<>(
                left.knowledgeSpaceIds());
        spaces.addAll(right.knowledgeSpaceIds());
        return spaces.stream().allMatch(space -> left
                .forKnowledgeSpace(space)
                .authorizationFingerprint()
                .equals(right.forKnowledgeSpace(space)
                        .authorizationFingerprint()));
    }

    private static boolean sameEvidence(
            List<SpaceReference> selected,
            List<SecureRetrievalCandidate> verified) {
        Map<UUID, SecureRetrievalCandidate> byChunk = verified.stream()
                .collect(Collectors.toMap(
                        SecureRetrievalCandidate::chunkId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (byChunk.size() != selected.size()) {
            return false;
        }
        return selected.stream().allMatch(candidate -> {
            EvidenceReference reference =
                    candidate.reference().evidence();
            SecureRetrievalCandidate canonical =
                    byChunk.get(reference.chunkId());
            return canonical != null
                    && reference.knowledgeAssetId()
                            .equals(canonical.knowledgeAssetId())
                    && reference.sourceRevisionId()
                            .equals(canonical.sourceRevisionId());
        });
    }

    private static RetrievedKnowledgeEvidence toEvidence(
            SecureRetrievalCandidate candidate,
            double score) {
        return new RetrievedKnowledgeEvidence(
                candidate.chunkId(),
                candidate.knowledgeAssetId(),
                candidate.sourceObjectId(),
                candidate.sourceRevisionId(),
                candidate.title(),
                candidate.content(),
                SourceCitationUri.safeForOutput(candidate.sourceUri()),
                candidate.startPage(),
                candidate.endPage(),
                candidate.heading(),
                0.0,
                score,
                score,
                candidate.ingestionAclSnapshotId(),
                candidate.currentAclSnapshotId(),
                candidate.authorizationModelId(),
                candidate.embeddingProfileId(),
                candidate.projectionGeneration());
    }

    private static PermissionAuditCommand evidenceAudit(
            CurrentActor actor,
            String requestId,
            String query,
            SecureRetrievalCandidate candidate,
            String authorizationModelId) {
        return new PermissionAuditCommand(
                actor.organizationId(),
                actor.userId(),
                "SEARCH",
                "KNOWLEDGE_EVIDENCE",
                candidate.chunkId().toString(),
                PermissionAuditDecision.ALLOW,
                "VERIFIED_GRAPH_RAG_EVIDENCE",
                authorizationModelId,
                requestId,
                query,
                candidate.ingestionAclSnapshotId(),
                candidate.currentAclSnapshotId(),
                candidate.authorizationModelId(),
                candidate.sourceRevisionId(),
                candidate.chunkId(),
                candidate.embeddingProfileId(),
                candidate.projectionGeneration());
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("q is required");
        }
        String normalized = query.strip();
        if (normalized.length()
                > retrievalProperties.maximumQueryLength()) {
            throw new IllegalArgumentException(
                    "q must not exceed "
                            + retrievalProperties.maximumQueryLength()
                            + " characters");
        }
        return normalized;
    }

    private int validateLimit(Integer requestedLimit) {
        int limit = requestedLimit == null
                ? Math.min(10, retrievalProperties.maximumResults())
                : requestedLimit;
        if (limit < 1
                || limit > retrievalProperties.maximumResults()) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and "
                            + retrievalProperties.maximumResults());
        }
        return limit;
    }

    private static String requestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String normalized = requestId.strip();
        return normalized.length() <= MAX_REQUEST_ID_LENGTH
                ? normalized
                : UUID.randomUUID().toString();
    }

    private static ProjectionNamespace namespace(
            UUID organizationId,
            UUID knowledgeSpaceId) {
        return new ProjectionNamespace(
                organizationId,
                "default",
                knowledgeSpaceId.toString());
    }

    private static java.util.function.Predicate<SpaceReference>
            distinctByChunk() {
        Set<UUID> seen = new LinkedHashSet<>();
        return candidate -> seen.add(
                candidate.reference().evidence().chunkId());
    }

    private record SpaceReference(
            UUID knowledgeSpaceId,
            LightRagQueryResult.Reference reference,
            double score) {

        private SpaceReference {
            Objects.requireNonNull(
                    knowledgeSpaceId,
                    "knowledgeSpaceId");
            Objects.requireNonNull(reference, "reference");
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException(
                        "score must be finite");
            }
        }
    }
}
