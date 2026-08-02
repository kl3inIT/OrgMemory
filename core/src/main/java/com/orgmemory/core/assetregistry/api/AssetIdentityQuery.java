package com.orgmemory.core.assetregistry.api;

import java.util.Optional;
import java.util.UUID;

/** Read access to canonical Asset identity without exposing Kernel persistence. */
public interface AssetIdentityQuery {

    Optional<AssetIdentity> findById(UUID organizationId, UUID assetId);

    Optional<AssetIdentity> findByCoordinate(
            UUID organizationId, String namespace, String slug);
}
