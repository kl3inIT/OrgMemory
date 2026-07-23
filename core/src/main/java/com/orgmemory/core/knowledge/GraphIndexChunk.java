package com.orgmemory.core.knowledge;

import java.util.Objects;
import java.util.UUID;

public record GraphIndexChunk(UUID id, int index, String content, String heading) {

    public GraphIndexChunk {
        Objects.requireNonNull(id, "id");
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        content = Objects.requireNonNull(content, "content").strip();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        heading = heading == null || heading.isBlank() ? null : heading.strip();
    }

    public GraphIndexChunk(UUID id, int index, String content) {
        this(id, index, content, null);
    }
}
