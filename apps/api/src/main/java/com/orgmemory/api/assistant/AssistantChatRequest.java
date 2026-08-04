package com.orgmemory.api.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record AssistantChatRequest(
        @NotBlank @Size(max = 4_000) String message,
        Integer limit,
        UUID conversationId,
        UUID modelActivationId) {
}
