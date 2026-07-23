package com.orgmemory.core.knowledge;

import java.util.List;

public record KnowledgeChunkDraft(
        int index,
        String content,
        String contentSha256,
        Integer tokenCount,
        Integer startPage,
        Integer endPage,
        String heading,
        Integer startChar,
        Integer endChar,
        List<Integer> sourceBlockIndexes,
        String canonicalTextSha256,
        float[] embedding) {

    public KnowledgeChunkDraft {
        sourceBlockIndexes = sourceBlockIndexes == null
                ? List.of()
                : List.copyOf(sourceBlockIndexes);
        embedding = embedding == null ? null : embedding.clone();
    }

    public KnowledgeChunkDraft(
            int index,
            String content,
            String contentSha256,
            Integer tokenCount,
            Integer startPage,
            Integer endPage,
            String heading,
            float[] embedding) {
        this(
                index,
                content,
                contentSha256,
                tokenCount,
                startPage,
                endPage,
                heading,
                null,
                null,
                List.of(),
                null,
                embedding);
    }

    @Override
    public float[] embedding() {
        return embedding == null ? null : embedding.clone();
    }
}
