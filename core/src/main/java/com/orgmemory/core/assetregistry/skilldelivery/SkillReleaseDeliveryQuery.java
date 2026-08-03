package com.orgmemory.core.assetregistry.skilldelivery;

import com.orgmemory.core.organization.CurrentActor;
import java.util.UUID;

public interface SkillReleaseDeliveryQuery {

    SkillReleaseDescriptor describe(
            CurrentActor actor, UUID assetId, UUID releaseId);

    SkillReleaseDescriptor describe(
            CurrentActor actor,
            String namespace,
            String slug,
            String version);

    SkillReleaseContent open(
            CurrentActor actor, UUID assetId, UUID releaseId);
}
