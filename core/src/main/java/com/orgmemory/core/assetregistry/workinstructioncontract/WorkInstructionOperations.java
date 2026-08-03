package com.orgmemory.core.assetregistry.workinstructioncontract;

import com.orgmemory.core.organization.CurrentActor;
import java.util.UUID;

public interface WorkInstructionOperations {

    WorkInstructionView follow(
            CurrentActor actor, UUID assetId, UUID releaseId);

    WorkInstructionView acknowledge(
            CurrentActor actor, UUID assetId, UUID releaseId);
}
