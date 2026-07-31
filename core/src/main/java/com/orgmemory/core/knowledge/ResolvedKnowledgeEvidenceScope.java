package com.orgmemory.core.knowledge;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ResolvedKnowledgeEvidenceScope(
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

    public AuthorizedEvidenceScope forKnowledgeSpace(UUID knowledgeSpaceId) {
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        return new AuthorizedEvidenceScope(
                organizationId,
                actorUserId,
                actorDepartmentId,
                actorExecutive,
                assetIdsByKnowledgeSpace.getOrDefault(
                        knowledgeSpaceId,
                        Set.of()),
                authorizationModelId,
                aclGenerationByKnowledgeSpace.getOrDefault(
                        knowledgeSpaceId,
                        0L),
                evaluatedAt);
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
