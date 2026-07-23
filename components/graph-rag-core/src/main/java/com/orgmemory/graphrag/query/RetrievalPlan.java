package com.orgmemory.graphrag.query;

import java.util.List;
import java.util.Objects;

public record RetrievalPlan(
        RetrievalStrategy strategy,
        int chunkSeedLimit,
        int entitySeedLimit,
        int relationSeedLimit,
        SecureContextBudget contextBudget) {

    public RetrievalPlan {
        Objects.requireNonNull(strategy, "strategy");
        if (chunkSeedLimit <= 0 || entitySeedLimit <= 0 || relationSeedLimit <= 0) {
            throw new IllegalArgumentException("seed limits must be positive");
        }
        Objects.requireNonNull(contextBudget, "contextBudget");
    }

    public static RetrievalPlan defaults(RetrievalStrategy strategy) {
        return new RetrievalPlan(
                strategy,
                60,
                60,
                60,
                SecureContextBudget.lightRagCompatibleDefaults());
    }

    public static RetrievalPlan secureMixDefaults() {
        return defaults(RetrievalStrategy.SECURE_MIX);
    }

    public List<RetrievalChannel> channels() {
        return strategy.channels();
    }

    public boolean includes(RetrievalChannel channel) {
        return strategy.includes(channel);
    }
}
