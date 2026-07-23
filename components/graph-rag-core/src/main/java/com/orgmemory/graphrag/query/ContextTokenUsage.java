package com.orgmemory.graphrag.query;

public record ContextTokenUsage(
        int systemPromptTokens,
        int queryTokens,
        int entityTokens,
        int relationTokens) {

    public ContextTokenUsage {
        if (systemPromptTokens < 0 || queryTokens < 0 || entityTokens < 0 || relationTokens < 0) {
            throw new IllegalArgumentException("token usage must be non-negative");
        }
    }
}
