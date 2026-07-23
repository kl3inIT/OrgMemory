package com.orgmemory.graphrag.authorization;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuthorizedGraphScope(
        UUID organizationId,
        UUID actorUserId,
        UUID actorDepartmentId,
        boolean actorExecutive,
        Set<UUID> authorizedAssetIds,
        String authorizationModelId,
        Instant evaluatedAt) {

    public AuthorizedGraphScope {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(actorUserId, "actorUserId");
        authorizedAssetIds = Set.copyOf(Objects.requireNonNull(authorizedAssetIds, "authorizedAssetIds"));
        authorizationModelId = Objects.requireNonNull(authorizationModelId, "authorizationModelId").strip();
        if (authorizationModelId.isEmpty()) {
            throw new IllegalArgumentException("authorizationModelId must not be blank");
        }
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }

    public boolean includes(UUID candidateOrganizationId, UUID candidateAssetId) {
        return organizationId.equals(candidateOrganizationId)
                && authorizedAssetIds.contains(candidateAssetId);
    }
}
