package com.orgmemory.graphrag.query;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class DeterministicRanker {

    private DeterministicRanker() {
    }

    public static <T> List<RankedItem<T>> rank(Collection<RankedItem<T>> candidates, int limit) {
        Objects.requireNonNull(candidates, "candidates");
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }
        return candidates.stream()
                .sorted(Comparator
                        .<RankedItem<T>>comparingDouble(RankedItem::score)
                        .reversed()
                        .thenComparing(RankedItem::stableKey))
                .limit(limit)
                .toList();
    }
}
