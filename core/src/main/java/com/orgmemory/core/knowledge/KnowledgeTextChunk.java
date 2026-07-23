package com.orgmemory.core.knowledge;

import java.util.Objects;

public record KnowledgeTextChunk(
        String content,
        Integer startPage,
        Integer endPage) {

    public KnowledgeTextChunk {
        content = Objects.requireNonNull(content, "content");
    }
}
