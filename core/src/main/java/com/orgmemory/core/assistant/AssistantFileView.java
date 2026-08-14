package com.orgmemory.core.assistant;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssistantFileView(
        @NotNull UUID id,
        @NotBlank String fileName,
        @NotBlank String mediaType,
        @Positive long contentLength,
        @NotNull AssistantFileStatus status,
        String failureCode,
        @NotNull Instant expiresAt,
        @NotNull Instant createdAt) {}
