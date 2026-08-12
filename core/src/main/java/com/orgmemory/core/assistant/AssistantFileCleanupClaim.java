package com.orgmemory.core.assistant;

import java.util.UUID;

public record AssistantFileCleanupClaim(
        UUID fileId,
        String objectKey,
        boolean expired) {}
