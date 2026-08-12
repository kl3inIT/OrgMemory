package com.orgmemory.api.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

record AssistantChatRequest(
        @NotBlank @Size(max = 8_000) String message,
        Integer limit,
        UUID conversationId,
        UUID modelActivationId,
        @Size(max = 3) List<UUID> evidenceBindingIds,
        @Size(max = 3) List<UUID> assistantFileIds) {

    AssistantChatRequest(
            String message,
            Integer limit,
            UUID conversationId,
            UUID modelActivationId) {
        this(message, limit, conversationId, modelActivationId, List.of(), List.of());
    }

    AssistantChatRequest(
            String message,
            Integer limit,
            UUID conversationId,
            UUID modelActivationId,
            List<UUID> evidenceBindingIds) {
        this(message, limit, conversationId, modelActivationId, evidenceBindingIds, List.of());
    }
}
