package com.orgmemory.core.assetregistry.kernel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Transaction-owning canonical queue around external Asset authorization projection. */
public interface AssetAuthorizationProjectionQueue {

    Optional<AssetAuthorizationBatch> claimForAsset(UUID organizationId, UUID assetId);

    List<AssetAuthorizationBatch> claimPending(int limit);

    void complete(AssetAuthorizationBatch batch, String authorizationModelId);

    void fail(AssetAuthorizationBatch batch, String code, String message);
}
