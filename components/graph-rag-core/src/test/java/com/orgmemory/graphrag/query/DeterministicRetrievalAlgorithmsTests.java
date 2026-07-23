package com.orgmemory.graphrag.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicRetrievalAlgorithmsTests {

    @Test
    void ranksByScoreThenStableKey() {
        List<RankedItem<String>> ranked = DeterministicRanker.rank(List.of(
                new RankedItem<>("b", "second", 0.8),
                new RankedItem<>("c", "third", 0.7),
                new RankedItem<>("a", "first", 0.8)), 3);

        assertEquals(List.of("a", "b", "c"), ranked.stream().map(RankedItem::stableKey).toList());
    }

    @Test
    void interleavesChannelsAndDropsDuplicateEvidence() {
        List<RankedItem<String>> merged = RoundRobinInterleaver.merge(List.of(
                List.of(
                        new RankedItem<>("entity-1", "entity", 1.0),
                        new RankedItem<>("shared", "entity shared", 0.8)),
                List.of(
                        new RankedItem<>("relation-1", "relation", 1.0),
                        new RankedItem<>("shared", "relation shared", 0.9)),
                List.of(new RankedItem<>("chunk-1", "chunk", 1.0))), 10);

        assertEquals(
                List.of("entity-1", "relation-1", "chunk-1", "shared"),
                merged.stream().map(RankedItem::stableKey).toList());
    }

    @Test
    void allocatesOnlyWholeRankedItems() {
        TokenAllocation<String> complete = WholeItemBudgetAllocator.allocate(
                List.of("aa", "bbb"),
                String::length,
                5);
        TokenAllocation<String> truncated = WholeItemBudgetAllocator.allocate(
                List.of("aa", "bbb", "c"),
                String::length,
                4);

        assertEquals(List.of("aa", "bbb"), complete.items());
        assertEquals(5, complete.usedTokens());
        assertFalse(complete.truncated());
        assertEquals(List.of("aa"), truncated.items());
        assertEquals(2, truncated.usedTokens());
        assertTrue(truncated.truncated());
    }
}
