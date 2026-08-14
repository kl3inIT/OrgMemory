package com.orgmemory.core.assistant;

import java.util.List;

public record AssistantFileChunkDraft(
        String content,
        String heading,
        Integer startPage,
        Integer endPage,
        int tokenCount,
        Integer sourceStartChar,
        Integer sourceEndChar,
        List<Integer> sourceBlockIndexes,
        String canonicalTextSha256,
        float[] embedding) {
    public AssistantFileChunkDraft {
        sourceBlockIndexes = List.copyOf(sourceBlockIndexes == null ? List.of() : sourceBlockIndexes);
        embedding = embedding == null ? null : embedding.clone();
    }

    @Override
    public float[] embedding() {
        return embedding.clone();
    }
}
