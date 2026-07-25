package com.orgmemory.core.assistant;

import java.util.UUID;

public record AssistantReleaseRef(
        UUID assetId,
        UUID releaseId,
        String releaseDigest) {
}
