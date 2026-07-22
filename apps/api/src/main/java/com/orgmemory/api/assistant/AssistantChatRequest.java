package com.orgmemory.api.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record AssistantChatRequest(
        @NotBlank @Size(max = 4_000) String message,
        Integer limit) {
}
