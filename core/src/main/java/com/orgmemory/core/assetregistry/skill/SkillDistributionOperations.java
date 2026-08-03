package com.orgmemory.core.assetregistry.skill;

import com.orgmemory.core.organization.CurrentActor;
import java.util.UUID;

public interface SkillDistributionOperations {

    SkillInstallManifest manifest(
            CurrentActor actor, UUID assetId, UUID releaseId);

    SkillInstallManifest manifest(
            CurrentActor actor,
            String namespace,
            String slug,
            String version);

    SkillPackageContent open(
            CurrentActor actor, UUID assetId, UUID releaseId);
}
