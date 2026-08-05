package com.orgmemory.core.assetregistry.skilldelivery;

import java.util.UUID;

/** Actor-authorized metadata for one exact installable Skill release. */
public record SkillReleaseSummary(
        UUID assetId,
        UUID releaseId,
        String namespace,
        String slug,
        String version,
        String title,
        String description,
        String releaseDigest) {
}
