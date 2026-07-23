package com.orgmemory.graphrag.query;

import java.util.Objects;

public record RankedItem<T>(String stableKey, T value, double score) {

    public RankedItem {
        stableKey = Objects.requireNonNull(stableKey, "stableKey").strip();
        if (stableKey.isEmpty()) {
            throw new IllegalArgumentException("stableKey must not be blank");
        }
        Objects.requireNonNull(value, "value");
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
    }
}
