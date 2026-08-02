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
        boolean authorizationReady) {

    public AssetIdentity {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        Objects.requireNonNull(portfolioState, "portfolioState");
    }
}
