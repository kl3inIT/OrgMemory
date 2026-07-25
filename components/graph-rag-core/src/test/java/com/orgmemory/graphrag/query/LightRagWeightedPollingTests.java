package com.orgmemory.graphrag.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LightRagWeightedPollingTests {

    @Test
    void portsTheLinearGradientSelectionAndRedistributesUnusedCapacity() {
        UUID a1 = id("a1");
        UUID a2 = id("a2");
        UUID a3 = id("a3");
        UUID b1 = id("b1");
        UUID c1 = id("c1");
        UUID c2 = id("c2");

        List<UUID> selected = LightRagQueryEngine.weightedPolling(
                List.of(
                        List.of(a1, a2, a3),
                        List.of(b1),
                        List.of(c1, c2)),
                3,
                1);

        assertEquals(List.of(a1, a2, a3, b1, c1, c2), selected);
    }

    @Test
    void keepsSingleGroupOrderAndHonorsTheMaximum() {
        UUID first = id("first");
        UUID second = id("second");
        UUID third = id("third");

        assertEquals(
                List.of(first, second),
                LightRagQueryEngine.weightedPolling(
                        List.of(List.of(first, second, third)), 2, 1));
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
