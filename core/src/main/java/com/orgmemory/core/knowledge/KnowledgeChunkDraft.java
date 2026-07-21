package com.orgmemory.core.knowledge;

public record KnowledgeChunkDraft(
        int index,
        String content,
        String contentSha256,
        Integer tokenCount,
        Integer startPage,
        Integer endPage,
        String heading,
        float[] embedding) {

    public KnowledgeChunkDraft {
        embedding = embedding == null ? null : embedding.clone();
    }

    @Override
    public float[] embedding() {
        return embedding == null ? null : embedding.clone();
    }
}
