package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable permission-verified evidence snapshot exposed to Graph use cases. */
public record VerifiedGraphEvidenceScope(
        UUID organizationId,
        UUID actorUserId,
        UUID actorDepartmentId,
        boolean actorExecutive,
        String authorizationModelId,
        Instant evaluatedAt,
        Map<UUID, Set<UUID>> assetIdsByKnowledgeSpace,
        Map<UUID, Long> aclGenerationByKnowledgeSpace) {

    public VerifiedGraphEvidenceScope {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(actorUserId, "actorUserId");
        authorizationModelId = required(
                authorizationModelId, "authorizationModelId");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        assetIdsByKnowledgeSpace = immutableSets(assetIdsByKnowledgeSpace);
        aclGenerationByKnowledgeSpace = Map.copyOf(Objects.requireNonNull(
                aclGenerationByKnowledgeSpace,
                "aclGenerationByKnowledgeSpace"));
        if (!aclGenerationByKnowledgeSpace.keySet()
                .equals(assetIdsByKnowledgeSpace.keySet())) {
            throw new IllegalArgumentException(
                    "ACL generations and Knowledge Space scopes must align");
        }
        if (aclGenerationByKnowledgeSpace.values().stream()
                .anyMatch(generation -> generation == null || generation < 0)) {
            throw new IllegalArgumentException(
                    "ACL generations must be non-negative");
        }
    }

    public boolean includesKnowledgeSpace(UUID knowledgeSpaceId) {
        return assetIdsByKnowledgeSpace.containsKey(
                Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId"));
    }

    public boolean includes(
            UUID knowledgeSpaceId,
            UUID candidateOrganizationId,
            UUID candidateAssetId) {
        return forKnowledgeSpace(knowledgeSpaceId)
                .includes(candidateOrganizationId, candidateAssetId);
    }

    public AuthorizedEvidenceScope forKnowledgeSpace(UUID knowledgeSpaceId) {
        UUID spaceId = Objects.requireNonNull(
                knowledgeSpaceId, "knowledgeSpaceId");
        return new AuthorizedEvidenceScope(
                organizationId,
                actorUserId,
                actorDepartmentId,
                actorExecutive,
                assetIdsByKnowledgeSpace.getOrDefault(spaceId, Set.of()),
                authorizationModelId,
                authorizationGeneration(spaceId),
                evaluatedAt);
    }

    public long authorizationGeneration(UUID knowledgeSpaceId) {
        return aclGenerationByKnowledgeSpace.getOrDefault(
                Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId"),
                0L);
    }

    public boolean hasSameAuthorizationFingerprint(
            VerifiedGraphEvidenceScope other,
            UUID knowledgeSpaceId) {
        Objects.requireNonNull(other, "other");
        return forKnowledgeSpace(knowledgeSpaceId)
                .authorizationFingerprint()
                .equals(other.forKnowledgeSpace(knowledgeSpaceId)
                        .authorizationFingerprint());
    }

    public boolean hasSameAssetsAndGeneration(
            VerifiedGraphEvidenceScope other,
            UUID knowledgeSpaceId) {
        Objects.requireNonNull(other, "other");
        return forKnowledgeSpace(knowledgeSpaceId)
                        .authorizedAssetIds()
                        .equals(other.forKnowledgeSpace(knowledgeSpaceId)
                                .authorizedAssetIds())
                && authorizationGeneration(knowledgeSpaceId)
                        == other.authorizationGeneration(knowledgeSpaceId);
    }

    SecureKnowledgeRetrievalStore.RetrievalScope toRetrievalScope() {
        LinkedHashSet<UUID> assetIds = new LinkedHashSet<>();
        assetIdsByKnowledgeSpace.values().forEach(assetIds::addAll);
        return new SecureKnowledgeRetrievalStore.RetrievalScope(
                organizationId,
                actorUserId,
                actorDepartmentId,
                actorExecutive,
                assetIds.stream().sorted().toList(),
                authorizationModelId,
                evaluatedAt);
    }

    private static Map<UUID, Set<UUID>> immutableSets(
            Map<UUID, Set<UUID>> source) {
        Map<UUID, Set<UUID>> copy = new LinkedHashMap<>();
        Objects.requireNonNull(source, "assetIdsByKnowledgeSpace")
                .forEach((space, assets) -> copy.put(
                        Objects.requireNonNull(space, "knowledgeSpaceId"),
                        Set.copyOf(Objects.requireNonNull(
                                assets, "assetIds"))));
        return Map.copyOf(copy);
    }

    private static String required(String value, String field) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }
}
