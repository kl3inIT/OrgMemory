package com.orgmemory.core.assetregistry.skilldelivery;

import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageArtifact;
import java.util.Objects;

public record SkillReleaseDescriptor(
        AssetConsumptionRelease release,
        SkillPackageArtifact artifact) {

    public SkillReleaseDescriptor {
        release = Objects.requireNonNull(release, "release");
        artifact = Objects.requireNonNull(artifact, "artifact");
    }
}
