package com.orgmemory.core.assistant.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * An unavailable turn used to publish only the exception class, and
 * {@code AssistantUnavailableException} is raised both when the retrieval pool is saturated and
 * when retrieval itself fails. On ZM that collapsed a 21% failure rate into one undifferentiated
 * series with no log line beside it, so there was nothing to attribute it with.
 *
 * <p>These hold the tag that separates them, and hold it to the same cardinality rule the rest
 * of this surface is held to.
 */
class DefaultAssistantTurnObservationConventionTests {

    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final long COMPLETED_AT_NANOS = 1_000_000L;

    private final DefaultAssistantTurnObservationConvention convention =
            new DefaultAssistantTurnObservationConvention();

    @Test
    void namesWhyAnUnavailableTurnWasUnavailable() {
        AssistantTurnObservationContext context = context();
        context.unavailable(COMPLETED_AT_NANOS, "assistant_retrieval_rejected");

        assertEquals(
                "assistant_retrieval_rejected",
                tag(context, "failure_code"),
                "saturation has to be separable from a broken retrieval");
    }

    /**
     * Absent-on-success would split the answered series in two on any backend that keys on the
     * full tag set, which is the comparison the panel exists to make.
     */
    @Test
    void saysNoneRatherThanNothingWhenTheTurnAnswered() {
        AssistantTurnObservationContext context = context();
        context.answered(COMPLETED_AT_NANOS, 3, 2);

        assertEquals("none", tag(context, "failure_code"));
    }

    @Test
    void publishesTheFailureCodeAsALowCardinalityTagRatherThanASpanOnlyOne() {
        AssistantTurnObservationContext context = context();
        context.unavailable(COMPLETED_AT_NANOS, "assistant_turn_failed");

        assertNotNull(
                find(convention.getLowCardinalityKeyValues(context), "failure_code"),
                "a span-only failure code cannot be grouped by on a dashboard");
    }

    /**
     * The failure code reaches a meter tag, so it is only safe while it stays a bounded machine
     * code. {@link AssistantTurnEvent} enforces that; this asserts the convention did not find
     * some other route to a tag.
     */
    @Test
    void carriesNoFreeTextOnTheLowCardinalitySurface() {
        AssistantTurnObservationContext context = context();
        context.unavailable(COMPLETED_AT_NANOS, "assistant_stream_failed");

        for (KeyValue keyValue : convention.getLowCardinalityKeyValues(context)) {
            assertTrue(
                    keyValue.getValue().matches("[a-z0-9_]{1,64}|true|false"),
                    () -> keyValue.getKey() + " carries " + keyValue.getValue()
                            + ", which is not a bounded machine value");
        }
    }

    private AssistantTurnObservationContext context() {
        return new AssistantTurnObservationContext(
                ORGANIZATION,
                AssistantTurnEvent.RetrievalEngine.GRAPH_RAG,
                0L);
    }

    private String tag(AssistantTurnObservationContext context, String key) {
        KeyValue found = find(convention.getLowCardinalityKeyValues(context), key);
        assertNotNull(found, () -> "no " + key + " tag was published");
        return found.getValue();
    }

    private static KeyValue find(KeyValues keyValues, String key) {
        for (KeyValue keyValue : keyValues) {
            if (keyValue.getKey().equals(key)) {
                return keyValue;
            }
        }
        return null;
    }
}
