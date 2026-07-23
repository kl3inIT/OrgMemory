package com.orgmemory.graphrag.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoundedBatcherTests {

    @Test
    void enforcesBothRecordAndPayloadLimitsWithoutDroppingOrder() {
        List<List<String>> batches = new ArrayList<>();

        BoundedBatcher.forEachBatch(
                List.of("aa", "bbbb", "c", "ddd", "ee"),
                3,
                6,
                String::length,
                batches::add);

        assertEquals(
                List.of(
                        List.of("aa", "bbbb"),
                        List.of("c", "ddd", "ee")),
                batches);
    }

    @Test
    void allowsOneOversizedRecordToMakeProgress() {
        List<List<String>> batches = new ArrayList<>();

        BoundedBatcher.forEachBatch(
                List.of("oversized", "next"),
                10,
                3,
                String::length,
                batches::add);

        assertEquals(
                List.of(List.of("oversized"), List.of("next")),
                batches);
    }
}
