package com.orgmemory.graphrag.query;

public record SecureContextBudget(
        int maxEntityTokens,
        int maxRelationTokens,
        int maxTotalTokens,
        int safetyBufferTokens) {

    public SecureContextBudget {
        if (maxEntityTokens <= 0 || maxRelationTokens <= 0 || maxTotalTokens <= 0) {
            throw new IllegalArgumentException("context token limits must be positive");
        }
        if (safetyBufferTokens < 0 || safetyBufferTokens >= maxTotalTokens) {
            throw new IllegalArgumentException("safety buffer must fit inside the total budget");
        }
        if (maxEntityTokens > maxTotalTokens || maxRelationTokens > maxTotalTokens) {
            throw new IllegalArgumentException("channel budgets must not exceed the total budget");
        }
    }

    public static SecureContextBudget lightRagCompatibleDefaults() {
        return new SecureContextBudget(6_000, 8_000, 30_000, 200);
    }

    public int availableChunkTokens(ContextTokenUsage usage) {
        if (usage.entityTokens() > maxEntityTokens) {
            throw new IllegalArgumentException("entity context exceeds its budget");
        }
        if (usage.relationTokens() > maxRelationTokens) {
            throw new IllegalArgumentException("relation context exceeds its budget");
        }
        long reserved = (long) usage.systemPromptTokens()
                + usage.queryTokens()
                + usage.entityTokens()
                + usage.relationTokens()
                + safetyBufferTokens;
        return (int) Math.max(0L, maxTotalTokens - reserved);
    }
}
