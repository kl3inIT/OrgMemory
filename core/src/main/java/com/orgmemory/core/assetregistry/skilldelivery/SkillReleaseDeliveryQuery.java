package com.orgmemory.core.assetregistry.skilldelivery;

import com.orgmemory.core.organization.CurrentActor;
import java.util.List;
import java.util.UUID;

public interface SkillReleaseDeliveryQuery {

    List<SkillReleaseSummary> search(
            CurrentActor actor, String query, int limit);

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
