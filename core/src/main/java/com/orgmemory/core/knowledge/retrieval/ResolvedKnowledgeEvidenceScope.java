package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.core.knowledge.search.KnowledgeEvidenceSelection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

record ResolvedKnowledgeEvidenceScope(
        UUID organizationId,
        UUID actorUserId,
        UUID actorDepartmentId,
        boolean actorExecutive,
        String authorizationModelId,
        Instant evaluatedAt,
        Map<UUID, Set<UUID>> assetIdsByKnowledgeSpace,
        Map<UUID, Long> aclGenerationByKnowledgeSpace) {

    public ResolvedKnowledgeEvidenceScope {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(actorUserId, "actorUserId");
        authorizationModelId = required(
                authorizationModelId,
                "authorizationModelId");
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

    public Set<UUID> allAssetIds() {
        LinkedHashSet<UUID> all = new LinkedHashSet<>();
        assetIdsByKnowledgeSpace.values().forEach(all::addAll);
        return Set.copyOf(all);
    }

    public Set<UUID> knowledgeSpaceIds() {
        return assetIdsByKnowledgeSpace.keySet();
    }

    public ResolvedKnowledgeEvidenceScope restrictTo(Set<UUID> permittedAssetIds) {
        Set<UUID> permitted = Set.copyOf(Objects.requireNonNull(
                permittedAssetIds,
                "permittedAssetIds"));
        Map<UUID, Set<UUID>> restrictedAssets = new LinkedHashMap<>();
        Map<UUID, Long> restrictedGenerations = new LinkedHashMap<>();
        assetIdsByKnowledgeSpace.forEach((spaceId, assetIds) -> {
            LinkedHashSet<UUID> intersection = new LinkedHashSet<>(assetIds);
            intersection.retainAll(permitted);
            if (!intersection.isEmpty()) {
                restrictedAssets.put(spaceId, Set.copyOf(intersection));
                restrictedGenerations.put(spaceId, aclGenerationByKnowledgeSpace.get(spaceId));
            }
        });
        return new ResolvedKnowledgeEvidenceScope(
                organizationId,
                actorUserId,
                actorDepartmentId,
                actorExecutive,
                authorizationModelId,
                evaluatedAt,
                restrictedAssets,
                restrictedGenerations);
    }

    public SecureKnowledgeRetrievalStore.RetrievalScope toRetrievalScope() {
        return new SecureKnowledgeRetrievalStore.RetrievalScope(
                organizationId,
                actorUserId,
                actorDepartmentId,
                actorExecutive,
                allAssetIds().stream().sorted().toList(),
                authorizationModelId,
                evaluatedAt);
    }

    public AuthorizedEvidenceScope forKnowledgeSpace(UUID knowledgeSpaceId) {
        return forKnowledgeSpace(
                knowledgeSpaceId,
                KnowledgeEvidenceSelection.unrestricted());
    }

    public AuthorizedEvidenceScope forKnowledgeSpace(
            UUID knowledgeSpaceId,
            KnowledgeEvidenceSelection selection) {
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        Objects.requireNonNull(selection, "selection");
        Set<UUID> spaceAssetIds = assetIdsByKnowledgeSpace.getOrDefault(
                knowledgeSpaceId,
                Set.of());
        return new AuthorizedEvidenceScope(
                organizationId,
                actorUserId,
                actorDepartmentId,
                actorExecutive,
                spaceAssetIds,
                authorizationModelId,
                aclGenerationByKnowledgeSpace.getOrDefault(
                        knowledgeSpaceId,
                        0L),
                evaluatedAt,
                selection.items().stream()
                        .filter(item -> spaceAssetIds.contains(item.knowledgeAssetId()))
                        .map(item -> new AuthorizedEvidenceScope.EvidenceIdentity(
                                item.knowledgeAssetId(),
                                item.sourceObjectId(),
                                item.sourceRevisionId()))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    private static Map<UUID, Set<UUID>> immutableSets(
            Map<UUID, Set<UUID>> source) {
        Map<UUID, Set<UUID>> copy = new LinkedHashMap<>();
        Objects.requireNonNull(source, "assetIdsByKnowledgeSpace")
                .forEach((space, assets) -> copy.put(
                        Objects.requireNonNull(space, "knowledgeSpaceId"),
                        Set.copyOf(Objects.requireNonNull(
                                assets,
                                "assetIds"))));
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
