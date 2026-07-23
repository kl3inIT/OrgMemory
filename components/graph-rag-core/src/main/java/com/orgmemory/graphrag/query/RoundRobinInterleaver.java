package com.orgmemory.graphrag.query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RoundRobinInterleaver {

    private RoundRobinInterleaver() {
    }

    public static <T> List<RankedItem<T>> merge(
            List<? extends List<RankedItem<T>>> rankedChannels,
            int limit) {
        Objects.requireNonNull(rankedChannels, "rankedChannels");
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }
        if (limit == 0 || rankedChannels.isEmpty()) {
            return List.of();
        }

        List<RankedItem<T>> merged = new ArrayList<>(Math.min(limit, 32));
        Set<String> seenKeys = new HashSet<>();
        int offset = 0;
        boolean foundAtOffset;
        do {
            foundAtOffset = false;
            for (List<RankedItem<T>> channel : rankedChannels) {
                Objects.requireNonNull(channel, "ranked channel");
                if (offset >= channel.size()) {
                    continue;
                }
                foundAtOffset = true;
                RankedItem<T> candidate = Objects.requireNonNull(channel.get(offset), "ranked item");
                if (seenKeys.add(candidate.stableKey())) {
                    merged.add(candidate);
                    if (merged.size() == limit) {
                        return List.copyOf(merged);
                    }
                }
            }
            offset++;
        } while (foundAtOffset);
        return List.copyOf(merged);
    }
}
