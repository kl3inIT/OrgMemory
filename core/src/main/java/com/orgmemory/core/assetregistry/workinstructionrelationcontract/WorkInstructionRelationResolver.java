package com.orgmemory.core.assetregistry.workinstructionrelationcontract;

import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.organization.CurrentActor;

public interface WorkInstructionRelationResolver {

    WorkInstructionRelations resolveRelations(
            CurrentActor actor, AssetConsumptionRelease authorizedRelease);
}
