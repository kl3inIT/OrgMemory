package com.orgmemory.core.assetregistry;

import java.util.List;
import java.util.UUID;

public record CapabilityPackDefinition(
        UUID packAssetId,
        UUID packReleaseId,
        String releaseDigest,
        String title,
        String versionLabel,
        CapabilityPackSpec.Purpose purpose,
        String audience,
        List<String> prerequisites,
        String expectedOutcome,
        List<String> completionCriteria,
        String reviewDate,
        String owner,
        boolean accessGap,
        List<Item> items) {

    public CapabilityPackDefinition {
        prerequisites = List.copyOf(prerequisites);
        completionCriteria = List.copyOf(completionCriteria);
        items = List.copyOf(items);
    }

    public record Item(
            String key,
            boolean required,
            int order,
            String kind,
            UUID resourceId,
            UUID pinnedVersionId,
            String title,
            String versionLabel,
            AssetAvailability availability) {
    }
}
