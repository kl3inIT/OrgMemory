package com.orgmemory.core.knowledge;

import java.util.Objects;
import java.util.List;

public record KnowledgeTextChunk(
        String content,
        Integer startPage,
        Integer endPage,
        Integer tokenCount,
        String heading,
        Integer startChar,
        Integer endChar,
        List<Integer> sourceBlockIndexes,
        String canonicalTextSha256) {

    public KnowledgeTextChunk {
        content = Objects.requireNonNull(content, "content");
        sourceBlockIndexes = sourceBlockIndexes == null
                ? List.of()
                : List.copyOf(sourceBlockIndexes);
    }

    public KnowledgeTextChunk(String content, Integer startPage, Integer endPage) {
        this(content, startPage, endPage, null, null, null, null, List.of(), null);
    }
}
