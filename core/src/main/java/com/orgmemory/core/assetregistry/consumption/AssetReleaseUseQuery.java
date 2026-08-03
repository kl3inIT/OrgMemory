package com.orgmemory.core.assetregistry.consumption;

import com.orgmemory.core.assetregistry.api.AssetNotFoundException;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.organization.CurrentActor;
import java.util.UUID;

/** Resolves an exact immutable release after enforcing its use permission. */
public interface AssetReleaseUseQuery {

    AssetConsumptionRelease releaseForUse(
            CurrentActor actor, UUID assetId, UUID releaseId);

    default AssetConsumptionRelease promptTemplateForUse(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        AssetConsumptionRelease release = releaseForUse(actor, assetId, releaseId);
        if (release.type() != AssetType.PROMPT_TEMPLATE) {
            throw new AssetNotFoundException();
        }
        return release;
    }

    default AssetConsumptionRelease workInstructionForUse(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        AssetConsumptionRelease release = releaseForUse(actor, assetId, releaseId);
        if (release.type() != AssetType.WORK_INSTRUCTION) {
            throw new AssetNotFoundException();
        }
        return release;
    }

    AssetConsumptionRelease latestReleaseForUse(
            CurrentActor actor, UUID assetId);
}
