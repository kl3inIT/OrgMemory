package com.orgmemory.core.assetregistry.api;

import java.util.Objects;
import java.util.UUID;

/** Immutable parent-facing view of canonical Asset identity and portfolio state. */
public record AssetIdentity(
        UUID organizationId,
        UUID id,
        AssetType type,
        String namespace,
        String slug,
        UUID knowledgeSpaceId,
        AssetPortfolioState portfolioState,
        boolean authorizationReady,
        UUID ownerUserId,
        AssetSharingState sharingState,
        long relationshipGeneration,
        long projectedRelationshipGeneration) {

    public AssetIdentity {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        Objects.requireNonNull(portfolioState, "portfolioState");
        Objects.requireNonNull(sharingState, "sharingState");
        if (relationshipGeneration <= 0
                || projectedRelationshipGeneration < 0
                || projectedRelationshipGeneration > relationshipGeneration) {
            throw new IllegalArgumentException("Asset relationship generations are inconsistent");
        }
    }
}
