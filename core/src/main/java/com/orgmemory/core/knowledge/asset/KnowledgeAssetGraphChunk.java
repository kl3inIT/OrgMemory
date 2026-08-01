package com.orgmemory.core.knowledge.asset;

import com.orgmemory.graphrag.model.FloatVector;
import java.util.Objects;
import java.util.UUID;

/** Immutable active chunk projection exposed to graph indexing. */
public record KnowledgeAssetGraphChunk(
        UUID id,
        int index,
        String content,
        String heading,
        int tokenCount,
        FloatVector embedding) {

    public KnowledgeAssetGraphChunk {
        Objects.requireNonNull(id, "id");
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        content = Objects.requireNonNull(content, "content");
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount must be non-negative");
        }
        Objects.requireNonNull(embedding, "embedding");
    }
}
