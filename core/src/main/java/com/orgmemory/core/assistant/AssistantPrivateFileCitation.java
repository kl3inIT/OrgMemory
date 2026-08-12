package com.orgmemory.core.assistant;

import java.util.UUID;

public record AssistantPrivateFileCitation(
        UUID chunkId,
        UUID fileId,
        long processingGeneration,
        String title,
        String heading,
        Integer startPage,
        Integer endPage,
        String content) {}
