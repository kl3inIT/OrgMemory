package com.orgmemory.core.assetregistry.api;

import java.util.UUID;

/** Atomic Asset portfolio transitions derived from parent-owned release outcomes. */
public interface AssetPortfolioCommand {

    AssetIdentity activateAfterRelease(UUID organizationId, UUID assetId);

    AssetIdentity startSunsettingAfterReleaseChange(UUID organizationId, UUID assetId);

    AssetIdentity retireAfterFinalWithdrawal(UUID organizationId, UUID assetId);
}
