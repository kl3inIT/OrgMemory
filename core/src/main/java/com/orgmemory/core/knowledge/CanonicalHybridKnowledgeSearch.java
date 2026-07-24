package com.orgmemory.core.knowledge;

import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CanonicalHybridKnowledgeSearch
        implements PermissionAwareKnowledgeSearch {

    static final String POLICY_VERSION =
            KnowledgeSearchAuthorizationService.POLICY_VERSION;
    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final String RESOURCE_TYPE = "knowledge_asset";
    private static final int RRF_RANK_CONSTANT = 60;
    private static final int MAX_REQUEST_ID_LENGTH = 128;

    private final SecureKnowledgeRetrievalStore store;
    private final KnowledgeEvidenceScopeResolver evidenceScopes;
    private final KnowledgeSearchAuthorizationService searchAuthorization;
    private final RelationshipAuthorizationSetPort authorization;
    private final QueryEmbeddingPort embeddings;
    private final PermissionAuditService audit;
    private final KnowledgeRetrievalProperties properties;

    CanonicalHybridKnowledgeSearch(
            SecureKnowledgeRetrievalStore store,
            KnowledgeEvidenceScopeResolver evidenceScopes,
            KnowledgeSearchAuthorizationService searchAuthorization,
            RelationshipAuthorizationSetPort authorization,
            QueryEmbeddingPort embeddings,
            PermissionAuditService audit,
            KnowledgeRetrievalProperties properties) {
        this.store = store;
        this.evidenceScopes = evidenceScopes;
        this.searchAuthorization = searchAuthorization;
        this.authorization = authorization;
        this.embeddings = embeddings;
        this.audit = audit;
        this.properties = properties;
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
        String entryModelId =
                searchAuthorization.require(
                        actor,
                        requestId,
                        normalizedQuery);
        ResolvedKnowledgeEvidenceScope evidenceScope;
        try {
            evidenceScope = evidenceScopes.resolve(actor, entryModelId);
        } catch (KnowledgeEvidenceScopeUnavailableException unavailable) {
            throw searchAuthorization.unavailable(
                    actor,
                    requestId,
                    normalizedQuery,
                    unavailable.reasonCode(),
                    unavailable.policyVersion());
        }
        Set<UUID> authorizedAssetIds = evidenceScope.allAssetIds();
        if (authorizedAssetIds.isEmpty()) {
            audit.record(searchAudit(
                    actor,
                    requestId,
                    normalizedQuery,
                    PermissionAuditDecision.ALLOW,
                    "NO_AUTHORIZED_KNOWLEDGE_ASSETS",
                    evidenceScope.authorizationModelId()));
            return new SecureKnowledgeSearchResult(requestId, List.of());
        }

        var scope = new SecureKnowledgeRetrievalStore.RetrievalScope(
                actor.organizationId(),
                actor.userId(),
                evidenceScope.actorDepartmentId(),
                evidenceScope.actorExecutive(),
                authorizedAssetIds.stream().sorted().toList(),
                evidenceScope.authorizationModelId(),
                evidenceScope.evaluatedAt());
        int candidateLimit = Math.multiplyExact(limit, properties.candidateMultiplier());
        List<SecureRetrievalCandidate> lexical = store.lexical(scope, normalizedQuery, candidateLimit);
        Optional<QueryEmbedding> queryEmbedding = embeddings.embed(actor.organizationId(), normalizedQuery);
        List<SecureRetrievalCandidate> semantic = queryEmbedding
                .map(embedding -> store.semantic(scope, embedding, candidateLimit))
                .orElseGet(List::of);
        validateCandidateSet(
                actor,
                requestId,
                normalizedQuery,
                lexical,
                authorizedAssetIds,
                evidenceScope.authorizationModelId());
        validateCandidateSet(
                actor,
                requestId,
                normalizedQuery,
                semantic,
                authorizedAssetIds,
                evidenceScope.authorizationModelId());

        List<ScoredCandidate> ranked = fuse(lexical, semantic).stream()
                .limit(limit)
                .toList();
        if (ranked.isEmpty()) {
            audit.record(searchAudit(
                    actor,
                    requestId,
                    normalizedQuery,
                    PermissionAuditDecision.ALLOW,
                    "NO_ELIGIBLE_EVIDENCE",
                    evidenceScope.authorizationModelId()));
            return new SecureKnowledgeSearchResult(requestId, List.of());
        }

        List<ResourceRef> rankedResources = ranked.stream()
                .map(candidate -> ResourceRef.of(
                        actor.organizationId(), RESOURCE_TYPE, candidate.candidate().knowledgeAssetId()))
                .distinct()
                .toList();
        var checked = authorization.batchCheck(new BatchAuthorizationQuery(
                actor.organizationId(), actor.principal(), CAN_VIEW, rankedResources));
        if (!checked.resolved() || checked.decisions().size() != rankedResources.size()) {
            throw searchAuthorization.unavailable(
                    actor,
                    requestId,
                    normalizedQuery,
                    checked.reasonCode(),
                    checked.policyVersion());
        }
        if (!evidenceScope.authorizationModelId().equals(checked.policyVersion())) {
            throw searchAuthorization.unavailable(
                    actor,
                    requestId,
                    normalizedQuery,
                    "AUTHORIZATION_MODEL_MISMATCH",
                    checked.policyVersion());
        }
        Set<UUID> allowedAssetIds = new LinkedHashSet<>();
        for (ResourceRef resource : rankedResources) {
            var decision = checked.decisions().get(resource);
            if (decision == null) {
                throw searchAuthorization.unavailable(
                        actor,
                        requestId,
                        normalizedQuery,
                        "OPENFGA_BATCH_INCOMPLETE",
                        checked.policyVersion());
            }
            if (!evidenceScope.authorizationModelId().equals(decision.policyVersion())) {
                throw searchAuthorization.unavailable(
                        actor,
                        requestId,
                        normalizedQuery,
                        "AUTHORIZATION_MODEL_MISMATCH",
                        decision.policyVersion());
            }
            if (decision.allowed()) {
                allowedAssetIds.add(UUID.fromString(resource.id()));
            }
        }

        List<ScoredCandidate> allowed = ranked.stream()
                .filter(candidate -> allowedAssetIds.contains(candidate.candidate().knowledgeAssetId()))
                .toList();
        Map<UUID, SecureRetrievalCandidate> rechecked = store.recheck(
                        scope,
                        allowed.stream().map(candidate -> candidate.candidate().chunkId()).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        SecureRetrievalCandidate::chunkId,
                        candidate -> candidate));

        List<RetrievedKnowledgeEvidence> evidence = new ArrayList<>();
        List<PermissionAuditCommand> auditCommands = new ArrayList<>();
        auditCommands.add(searchAudit(
                actor,
                requestId,
                normalizedQuery,
                PermissionAuditDecision.ALLOW,
                "SECURE_RETRIEVAL_APPLIED",
                evidenceScope.authorizationModelId()));
        for (ScoredCandidate scored : allowed) {
            SecureRetrievalCandidate canonical = rechecked.get(scored.candidate().chunkId());
            if (canonical == null
                    || !canonical.knowledgeAssetId().equals(scored.candidate().knowledgeAssetId())) {
                auditCommands.add(evidenceAudit(
                        actor,
                        requestId,
                        normalizedQuery,
                        scored.candidate(),
                        PermissionAuditDecision.DENY,
                        "CANONICAL_RECHECK_DENIED"));
                continue;
            }
            auditCommands.add(evidenceAudit(
                    actor,
                    requestId,
                    normalizedQuery,
                    canonical,
                    PermissionAuditDecision.ALLOW,
                    "VERIFIED_EVIDENCE"));
            evidence.add(new RetrievedKnowledgeEvidence(
                    canonical.chunkId(),
                    canonical.knowledgeAssetId(),
                    canonical.sourceObjectId(),
                    canonical.sourceRevisionId(),
                    canonical.title(),
                    canonical.content(),
                    SourceCitationUri.safeForOutput(canonical.sourceUri()),
                    canonical.startPage(),
                    canonical.endPage(),
                    canonical.heading(),
                    scored.lexicalScore(),
                    scored.vectorScore(),
                    scored.relevanceScore(),
                    canonical.ingestionAclSnapshotId(),
                    canonical.currentAclSnapshotId(),
                    canonical.authorizationModelId(),
                    canonical.embeddingProfileId(),
                    canonical.projectionGeneration()));
        }
        audit.recordAll(auditCommands);
        return new SecureKnowledgeSearchResult(requestId, evidence);
    }

    private void validateCandidateSet(
            CurrentActor actor,
            String requestId,
            String query,
            List<SecureRetrievalCandidate> candidates,
            Set<UUID> authorizedAssetIds,
            String policyVersion) {
        boolean invalid = candidates.stream()
                .anyMatch(candidate -> !actor.organizationId().equals(candidate.organizationId())
                        || !authorizedAssetIds.contains(candidate.knowledgeAssetId()));
        if (invalid) {
            throw searchAuthorization.unavailable(
                    actor,
                    requestId,
                    query,
                    "RETRIEVAL_AUTHORIZATION_BOUNDARY_VIOLATION",
                    policyVersion);
        }
    }

    private List<ScoredCandidate> fuse(
            List<SecureRetrievalCandidate> lexical,
            List<SecureRetrievalCandidate> semantic) {
        Map<UUID, MutableScore> fused = new LinkedHashMap<>();
        addRanks(fused, lexical, true);
        addRanks(fused, semantic, false);
        return fused.values().stream()
                .map(MutableScore::freeze)
                .sorted(Comparator.comparingDouble(ScoredCandidate::relevanceScore).reversed()
                        .thenComparing(candidate -> candidate.candidate().chunkId()))
                .toList();
    }

    private static void addRanks(
            Map<UUID, MutableScore> fused,
            List<SecureRetrievalCandidate> candidates,
            boolean lexical) {
        Set<UUID> countedAssets = new LinkedHashSet<>();
        int rank = 0;
        for (SecureRetrievalCandidate candidate : candidates) {
            if (!countedAssets.add(candidate.knowledgeAssetId())) {
                continue;
            }
            MutableScore score = fused.computeIfAbsent(candidate.chunkId(), ignored -> new MutableScore(candidate));
            score.relevance += 1.0d / (RRF_RANK_CONSTANT + rank + 1);
            if (lexical) {
                score.lexical = candidate.score();
            } else {
                score.vector = candidate.score();
            }
            rank++;
        }
    }

    private PermissionAuditCommand searchAudit(
            CurrentActor actor,
            String requestId,
            String query,
            PermissionAuditDecision decision,
            String reason,
            String policyVersion) {
        return searchAuthorization.command(
                actor,
                requestId,
                query,
                decision,
                reason,
                policyVersion);
    }

    private PermissionAuditCommand evidenceAudit(
            CurrentActor actor,
            String requestId,
            String query,
            SecureRetrievalCandidate candidate,
            PermissionAuditDecision decision,
            String reason) {
        return new PermissionAuditCommand(
                actor.organizationId(),
                actor.userId(),
                "SEARCH",
                "KNOWLEDGE_EVIDENCE",
                candidate.chunkId().toString(),
                decision,
                reason,
                POLICY_VERSION,
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
        if (normalized.length() > properties.maximumQueryLength()) {
            throw new IllegalArgumentException(
                    "q must not exceed " + properties.maximumQueryLength() + " characters");
        }
        return normalized;
    }

    private int validateLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? Math.min(10, properties.maximumResults()) : requestedLimit;
        if (limit < 1 || limit > properties.maximumResults()) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and " + properties.maximumResults());
        }
        return limit;
    }

    private static String requestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String normalized = requestId.strip();
        return normalized.length() <= MAX_REQUEST_ID_LENGTH ? normalized : UUID.randomUUID().toString();
    }

    private static final class MutableScore {
        private final SecureRetrievalCandidate candidate;
        private double lexical;
        private double vector;
        private double relevance;

        private MutableScore(SecureRetrievalCandidate candidate) {
            this.candidate = candidate;
        }

        private ScoredCandidate freeze() {
            return new ScoredCandidate(candidate, lexical, vector, relevance);
        }
    }

    private record ScoredCandidate(
            SecureRetrievalCandidate candidate,
            double lexicalScore,
            double vectorScore,
            double relevanceScore) {
    }
}
