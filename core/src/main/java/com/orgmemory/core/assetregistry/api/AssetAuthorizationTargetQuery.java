package com.orgmemory.core.assetregistry.api;

import java.util.Optional;
import java.util.UUID;

/** Fail-closed authorization target lookup backed by canonical Asset identity. */
public interface AssetAuthorizationTargetQuery {

    Optional<AssetAuthorizationTarget> find(UUID organizationId, UUID assetId);
}
