package com.orgmemory.core.knowledge.asset;

import com.orgmemory.graphrag.model.FloatVector;
import java.util.Objects;
import java.util.UUID;

public record KnowledgeChunkProjection(
        UUID id,
        int index,
        String content,
        String heading,
        int tokenCount,
        FloatVector embedding) {

    public KnowledgeChunkProjection {
        Objects.requireNonNull(id, "id");
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        content = Objects.requireNonNull(content, "content").strip();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        heading = heading == null || heading.isBlank() ? null : heading.strip();
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount must be non-negative");
        }
        Objects.requireNonNull(embedding, "embedding");
    }
}
