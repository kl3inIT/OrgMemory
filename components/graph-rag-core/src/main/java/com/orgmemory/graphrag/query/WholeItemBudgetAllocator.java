package com.orgmemory.graphrag.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

public final class WholeItemBudgetAllocator {

    private WholeItemBudgetAllocator() {
    }

    public static <T> TokenAllocation<T> allocate(
            List<T> rankedItems,
            ToIntFunction<T> tokenCounter,
            int maxTokens) {
        Objects.requireNonNull(rankedItems, "rankedItems");
        Objects.requireNonNull(tokenCounter, "tokenCounter");
        if (maxTokens < 0) {
            throw new IllegalArgumentException("maxTokens must be non-negative");
        }

        List<T> selected = new ArrayList<>();
        int usedTokens = 0;
        for (T item : rankedItems) {
            int itemTokens = tokenCounter.applyAsInt(Objects.requireNonNull(item, "ranked item"));
            if (itemTokens < 0) {
                throw new IllegalArgumentException("token count must be non-negative");
            }
            if ((long) usedTokens + itemTokens > maxTokens) {
                return new TokenAllocation<>(selected, usedTokens, true);
            }
            selected.add(item);
            usedTokens += itemTokens;
        }
        return new TokenAllocation<>(selected, usedTokens, false);
    }
}
