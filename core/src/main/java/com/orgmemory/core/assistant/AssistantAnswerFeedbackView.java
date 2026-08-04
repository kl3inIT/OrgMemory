package com.orgmemory.core.assistant;

import java.time.Instant;
import java.util.UUID;

public record AssistantAnswerFeedbackView(
        UUID messageId,
        AssistantAnswerSentiment sentiment,
        Instant updatedAt) {
}
