package com.orgmemory.core.assetregistry;

import java.time.Instant;
import java.util.UUID;

public record WorkInstructionView(
        UUID assetId,
        UUID releaseId,
        String releaseDigest,
        String title,
        String versionLabel,
        WorkInstructionSpec instruction,
        boolean acknowledged,
        Instant acknowledgedAt) {
}
