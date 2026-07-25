package com.orgmemory.core.assetregistry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PackJourney(
        UUID assignmentId,
        UUID packAssetId,
        UUID packReleaseId,
        String releaseDigest,
        String title,
        String versionLabel,
        CapabilityPackSpec.Purpose purpose,
        String audience,
        String expectedOutcome,
        PackAssignmentStatus status,
        boolean accessGap,
        int completedAccessibleItems,
        List<Item> items,
        Instant startedAt,
        Instant completedAt) {

    public PackJourney {
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
            AssetAvailability availability,
            boolean completed,
            Instant completedAt) {
    }
}
