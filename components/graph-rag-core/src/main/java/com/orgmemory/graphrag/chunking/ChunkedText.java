package com.orgmemory.graphrag.chunking;

import java.util.Objects;

public record ChunkedText(
        int order,
        String content,
        int tokenCount,
        String heading,
        ChunkProvenance provenance) {

    public ChunkedText {
        if (order < 0) {
            throw new IllegalArgumentException("chunk order must not be negative");
        }
        content = Objects.requireNonNull(content, "content").strip();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("chunk content must not be blank");
        }
        if (tokenCount <= 0) {
            throw new IllegalArgumentException("chunk token count must be positive");
        }
        heading = heading == null || heading.isBlank() ? null : heading.strip();
        Objects.requireNonNull(provenance, "provenance");
    }
}
