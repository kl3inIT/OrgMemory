package com.orgmemory.graphrag.query;

import java.util.List;
import java.util.Objects;

public record TokenAllocation<T>(List<T> items, int usedTokens, boolean truncated) {

    public TokenAllocation {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (usedTokens < 0) {
            throw new IllegalArgumentException("usedTokens must be non-negative");
        }
    }
}
