package com.orgmemory.core.assetregistry.skillcleanup;

public record SkillPackageCleanupSummary(
        int deleted,
        int retainedByImmutableReference,
        int retryScheduled,
        int alreadyResolved) {

    public SkillPackageCleanupSummary {
        if (deleted < 0
                || retainedByImmutableReference < 0
                || retryScheduled < 0
                || alreadyResolved < 0) {
            throw new IllegalArgumentException(
                    "Skill package cleanup counts cannot be negative");
        }
    }

    public boolean isEmpty() {
        return deleted == 0
                && retainedByImmutableReference == 0
                && retryScheduled == 0
                && alreadyResolved == 0;
    }
}
