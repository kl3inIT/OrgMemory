package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Current-permission boundary for opening original citation evidence. */
@Service
class CanonicalEvidenceAuthorizationService {

    private static final int HYDRATION_BATCH_SIZE = 20;
    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final String RESOURCE_TYPE = "knowledge_asset";
    private static final OpenFgaBatchRecheck.ReasonRule RESULT_REASON =
            OpenFgaBatchRecheck.ReasonRule.resultReason();
    private static final OpenFgaBatchRecheck.ReasonRule CITATION_DENIED =
            OpenFgaBatchRecheck.ReasonRule.fixed(
                    "CITATION_OPENFGA_RECHECK_DENIED");
    private static final OpenFgaBatchRecheck.ReasonMapping RECHECK_REASONS =
            new OpenFgaBatchRecheck.ReasonMapping(
                    RESULT_REASON,
                    RESULT_REASON,
                    RESULT_REASON,
                    CITATION_DENIED,
                    CITATION_DENIED,
                    CITATION_DENIED);

    private final KnowledgeSearchAuthorizationService searchAuthorization;
    private final KnowledgeEvidenceScopeResolver evidenceScopes;
    private final OpenFgaBatchRecheck batchRecheck;
    private final SecureKnowledgeRetrievalStore canonicalEvidence;

    CanonicalEvidenceAuthorizationService(
            KnowledgeSearchAuthorizationService searchAuthorization,
            KnowledgeEvidenceScopeResolver evidenceScopes,
            RelationshipAuthorizationSetPort authorization,
            SecureKnowledgeRetrievalStore canonicalEvidence) {
        this.searchAuthorization = searchAuthorization;
        this.evidenceScopes = evidenceScopes;
        this.batchRecheck = new OpenFgaBatchRecheck(authorization);
        this.canonicalEvidence = canonicalEvidence;
    }

    @Transactional(readOnly = true)
    Verification verify(
            CurrentActor actor,
            String requestId,
            String auditQuery,
            Collection<UUID> chunkIds) {
        Objects.requireNonNull(actor, "actor");
        String normalizedRequestId = required(requestId, "requestId");
        String normalizedAuditQuery = required(auditQuery, "auditQuery");
        List<UUID> expected = List.copyOf(
                Objects.requireNonNull(chunkIds, "chunkIds"));
        if (expected.isEmpty()) {
            throw new IllegalArgumentException(
                    "chunkIds must not be empty");
        }
        if (expected.stream().anyMatch(Objects::isNull)
                || expected.stream().distinct().count()
                        != expected.size()) {
            throw new IllegalArgumentException(
                    "chunkIds must be unique and non-null");
        }

        String authorizationModelId = searchAuthorization.require(
                actor,
                normalizedRequestId,
                normalizedAuditQuery);

        ResolvedKnowledgeEvidenceScope current = resolve(
                actor,
                authorizationModelId,
                normalizedRequestId,
                normalizedAuditQuery);
        List<SecureRetrievalCandidate> currentCandidates = findCanonical(
                current,
                expected,
                authorizationModelId);
        verifyOpenFga(
                actor,
                currentCandidates,
                normalizedRequestId,
                normalizedAuditQuery,
                authorizationModelId);
        return new Verification(
                authorizationModelId,
                currentCandidates);
    }

    @Transactional(readOnly = true)
    Verification filterVisible(
            CurrentActor actor,
            String requestId,
            String auditQuery,
            Collection<UUID> chunkIds) {
        Objects.requireNonNull(actor, "actor");
        String normalizedRequestId = required(requestId, "requestId");
        String normalizedAuditQuery = required(auditQuery, "auditQuery");
        List<UUID> expected = List.copyOf(Objects.requireNonNull(chunkIds, "chunkIds"));
        if (expected.isEmpty()) {
            return new Verification("none", List.of());
        }
        if (expected.size() > 100
                || expected.stream().anyMatch(Objects::isNull)
                || expected.stream().distinct().count() != expected.size()) {
            throw new IllegalArgumentException(
                    "chunkIds must contain at most 100 unique non-null values");
        }

        String authorizationModelId = searchAuthorization.require(
                actor, normalizedRequestId, normalizedAuditQuery);
        ResolvedKnowledgeEvidenceScope current = resolve(
                actor,
                authorizationModelId,
                normalizedRequestId,
                normalizedAuditQuery);
        if (current.allAssetIds().isEmpty()) {
            return new Verification(authorizationModelId, List.of());
        }

        Map<UUID, SecureRetrievalCandidate> candidateByChunk;
        try {
            candidateByChunk = canonicalEvidence.recheck(
                            retrievalScope(current), expected)
                    .stream()
                    .collect(Collectors.toMap(
                            SecureRetrievalCandidate::chunkId,
                            candidate -> candidate,
                            (left, right) -> {
                                throw new IllegalStateException(
                                        "Canonical citation hydration returned duplicate chunks");
                            },
                            LinkedHashMap::new));
        } catch (IllegalStateException invalid) {
            throw searchAuthorization.unavailable(
                    actor,
                    normalizedRequestId,
                    normalizedAuditQuery,
                    "CITATION_HYDRATION_INVALID",
                    authorizationModelId,
                    invalid);
        }

        List<SecureRetrievalCandidate> canonical = expected.stream()
                .map(candidateByChunk::get)
                .filter(Objects::nonNull)
                .toList();
        List<ResourceRef> resources = canonical.stream()
                .map(candidate -> ResourceRef.of(
                        actor.organizationId(),
                        RESOURCE_TYPE,
                        candidate.knowledgeAssetId()))
                .distinct()
                .toList();
        java.util.Set<String> allowedAssets = new java.util.LinkedHashSet<>();
        for (int offset = 0; offset < resources.size(); offset += HYDRATION_BATCH_SIZE) {
            List<ResourceRef> batch = resources.subList(
                    offset, Math.min(offset + HYDRATION_BATCH_SIZE, resources.size()));
            var rechecked = batchRecheck.recheck(
                    new BatchAuthorizationQuery(
                            actor.organizationId(), actor.principal(), CAN_VIEW, batch),
                    authorizationModelId,
                    OpenFgaBatchRecheck.ResultPolicy.FILTER_DENIED,
                    RECHECK_REASONS);
            if (!rechecked.succeeded()) {
                var failure = rechecked.failure();
                throw searchAuthorization.unavailable(
                        actor,
                        normalizedRequestId,
                        normalizedAuditQuery,
                        failure.reasonCode(),
                        failure.policyVersion());
            }
            rechecked.allowedResources().stream()
                    .map(ResourceRef::id)
                    .forEach(allowedAssets::add);
        }
        return new Verification(
                authorizationModelId,
                canonical.stream()
                        .filter(candidate -> allowedAssets.contains(
                                candidate.knowledgeAssetId().toString()))
                        .toList());
    }

    private ResolvedKnowledgeEvidenceScope resolve(
            CurrentActor actor,
            String authorizationModelId,
            String requestId,
            String auditQuery) {
        try {
            return evidenceScopes.resolve(actor, authorizationModelId);
        } catch (KnowledgeEvidenceScopeUnavailableException unavailable) {
            throw searchAuthorization.unavailable(
                    actor,
                    requestId,
                    auditQuery,
                    unavailable.reasonCode(),
                    unavailable.policyVersion());
        }
    }

    private List<SecureRetrievalCandidate> findCanonical(
            ResolvedKnowledgeEvidenceScope scope,
            List<UUID> expectedChunkIds,
            String authorizationModelId) {
        if (scope.allAssetIds().isEmpty()) {
            throw notVisible(
                    "CITATION_NOT_VISIBLE",
                    authorizationModelId);
        }
        List<SecureRetrievalCandidate> candidates =
                canonicalEvidence.recheck(
                        retrievalScope(scope),
                        expectedChunkIds);
        Map<UUID, SecureRetrievalCandidate> candidateByChunk =
                candidates.stream().collect(Collectors.toMap(
                        SecureRetrievalCandidate::chunkId,
                        candidate -> candidate,
                        (left, right) -> {
                            throw notVisible(
                                    "CITATION_EVIDENCE_SET_INVALID",
                                    authorizationModelId);
                        },
                        LinkedHashMap::new));
        if (candidateByChunk.size() != expectedChunkIds.size()) {
            throw notVisible(
                    "CITATION_NOT_VISIBLE",
                    authorizationModelId);
        }
        for (UUID chunkId : expectedChunkIds) {
            if (!candidateByChunk.containsKey(chunkId)) {
                throw notVisible(
                        "CITATION_NOT_VISIBLE",
                        authorizationModelId);
            }
        }
        return expectedChunkIds.stream()
                .map(candidateByChunk::get)
                .toList();
    }

    private void verifyOpenFga(
            CurrentActor actor,
            List<SecureRetrievalCandidate> candidates,
            String requestId,
            String auditQuery,
            String authorizationModelId) {
        List<ResourceRef> resources = candidates.stream()
                .map(candidate -> ResourceRef.of(
                        actor.organizationId(),
                        RESOURCE_TYPE,
                        candidate.knowledgeAssetId()))
                .distinct()
                .toList();
        var rechecked = batchRecheck.recheck(
                new BatchAuthorizationQuery(
                        actor.organizationId(),
                        actor.principal(),
                        CAN_VIEW,
                        resources),
                authorizationModelId,
                OpenFgaBatchRecheck.ResultPolicy.REQUIRE_ALL_ALLOWED,
                RECHECK_REASONS);
        if (!rechecked.succeeded()) {
            var failure = rechecked.failure();
            if (failure.kind()
                    == OpenFgaBatchRecheck.FailureKind.MISSING_DECISION
                    || failure.kind()
                            == OpenFgaBatchRecheck.FailureKind.DECISION_POLICY_MISMATCH
                    || failure.kind()
                            == OpenFgaBatchRecheck.FailureKind.DENIED) {
                throw notVisible(
                        failure.reasonCode(),
                        authorizationModelId);
            }
            throw searchAuthorization.unavailable(
                    actor,
                    requestId,
                    auditQuery,
                    failure.reasonCode(),
                    failure.policyVersion());
        }
    }

    private static SecureKnowledgeRetrievalStore.RetrievalScope
            retrievalScope(ResolvedKnowledgeEvidenceScope scope) {
        return scope.toRetrievalScope();
    }

    private static CanonicalEvidenceAuthorizationException notVisible(
            String reason,
            String authorizationModelId) {
        return new CanonicalEvidenceAuthorizationException(
                reason,
                authorizationModelId);
    }

    private static String required(String value, String field) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    record Verification(
            String authorizationModelId,
            List<SecureRetrievalCandidate> candidates) {

        Verification {
            candidates = List.copyOf(candidates);
        }
    }

}
