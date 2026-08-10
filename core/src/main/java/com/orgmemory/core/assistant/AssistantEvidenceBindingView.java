package com.orgmemory.core.assistant;

import java.time.Instant;
import java.util.UUID;

public record AssistantEvidenceBindingView(
        UUID id,
        UUID conversationId,
        UUID sourceObjectId,
        UUID sourceRevisionId,
        UUID knowledgeAssetId,
        String title,
        String fileName,
        AssistantEvidenceStatus status,
        String failureCode,
        Instant createdAt) {
}
