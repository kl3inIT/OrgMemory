package com.orgmemory.core.assistant;

import java.time.Instant;
import java.util.UUID;

public record AssistantConversationSummary(
        UUID id,
        String title,
        Instant lastActivityAt,
        long messageCount) {
}
