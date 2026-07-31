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

    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final String RESOURCE_TYPE = "knowledge_asset";

    private final KnowledgeSearchAuthorizationService searchAuthorization;
    private final KnowledgeEvidenceScopeResolver evidenceScopes;
    private final RelationshipAuthorizationSetPort authorization;
    private final SecureKnowledgeRetrievalStore canonicalEvidence;

    CanonicalEvidenceAuthorizationService(
            KnowledgeSearchAuthorizationService searchAuthorization,
            KnowledgeEvidenceScopeResolver evidenceScopes,
            RelationshipAuthorizationSetPort authorization,
            SecureKnowledgeRetrievalStore canonicalEvidence) {
        this.searchAuthorization = searchAuthorization;
        this.evidenceScopes = evidenceScopes;
        this.authorization = authorization;
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
                    auditQuery,
                    checked.reasonCode(),
                    checked.policyVersion());
        }
        for (ResourceRef resource : resources) {
            var decision = checked.decisions().get(resource);
            if (decision == null
                    || !decision.allowed()
                    || !authorizationModelId.equals(
                            decision.policyVersion())) {
                throw notVisible(
                        "CITATION_OPENFGA_RECHECK_DENIED",
                        authorizationModelId);
            }
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
