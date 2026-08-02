package com.orgmemory.core.assetregistry.api;

import java.util.UUID;

/** Projects the canonical authorization relationships for one Asset. */
public interface AssetAuthorizationProjectionCommand {

    void project(UUID organizationId, UUID assetId);
}
