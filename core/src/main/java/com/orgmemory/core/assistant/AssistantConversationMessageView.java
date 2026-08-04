package com.orgmemory.core.assistant;

import java.time.Instant;
import java.util.UUID;

public record AssistantConversationMessageView(
        UUID id,
        AssistantConversationRole role,
        String content,
        long sequence,
        Instant occurredAt,
        AssistantAnswerSentiment feedback) {
}
