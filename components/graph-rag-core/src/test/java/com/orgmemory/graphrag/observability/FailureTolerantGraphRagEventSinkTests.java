package com.orgmemory.graphrag.observability;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Before this wrapper existed, every producer caught the sink's failure and dropped it, so a
 * backend that had been failing since startup was indistinguishable from a quiet one. These
 * tests hold both halves of the fix in place: the work still succeeds, and the failure is
 * still countable afterwards.
 */
class FailureTolerantGraphRagEventSinkTests {

    @Test
    void letsTheObservedWorkSucceedWhenTheBackendIsDown() {
        var sink = GraphRagEventSink.failureTolerant(event -> {
            throw new IllegalStateException("collector down");
        });

        assertDoesNotThrow(() -> sink.emit(event()));
    }

    @Test
    void countsEveryDroppedEventSoABrokenBackendStopsBeingInvisible() {
        var sink = GraphRagEventSink.failureTolerant(event -> {
            throw new IllegalStateException("collector down");
        });

        sink.emit(event());
        sink.emit(event());
        sink.emit(event());

        assertEquals(3, sink.swallowedFailureCount());
        assertEquals(IllegalStateException.class.getName(), sink.lastFailureType());
    }

    @Test
    void reportsNothingWhileTheBackendIsHealthy() {
        var received = new java.util.ArrayList<GraphRagEventSink.GraphRagEvent>();
        var sink = GraphRagEventSink.failureTolerant(received::add);

        sink.emit(event());

        assertEquals(1, received.size(), "a healthy backend must still receive the event");
        assertEquals(0, sink.swallowedFailureCount());
        assertNull(sink.lastFailureType());
    }

    @Test
    void keepsCountingAndReportingTheLatestKindWhenTheBackendAlternatesFailures() {
        var kinds = new java.util.ArrayDeque<RuntimeException>(java.util.List.of(
                new IllegalStateException("timeout"),
                new IllegalArgumentException("connection refused"),
                new IllegalStateException("timeout again")));
        var sink = GraphRagEventSink.failureTolerant(event -> {
            throw kinds.removeFirst();
        });

        sink.emit(event());
        sink.emit(event());
        sink.emit(event());

        // A -> B -> A is what a timeout that retries as a connection failure looks like.
        // Every one is counted; the recurrence of A must not produce a second log line, which
        // is why reporting is keyed on the set of kinds seen rather than on the previous one.
        assertEquals(3, sink.swallowedFailureCount());
        assertEquals(2, sink.reportedFailureTypeCount(), "the recurrence of A must not log again");
        assertEquals(IllegalStateException.class.getName(), sink.lastFailureType());
    }

    @Test
    void namesOnlyTheFailureTypeSoATelemetryErrorCannotQuoteTheEventItCouldNotSend() {
        var sink = GraphRagEventSink.failureTolerant(event -> {
            throw new IllegalStateException(
                    "POST /v1/traces failed for query 'quarterly revenue for ACME'");
        });

        sink.emit(event());

        assertEquals(IllegalStateException.class.getName(), sink.lastFailureType());
        assertFalse(sink.lastFailureType().contains("ACME"));
    }

    private static GraphRagEventSink.GraphRagEvent event() {
        return new GraphRagEventSink.GraphRagEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                GraphRagEventSink.Stage.RETRIEVE,
                GraphRagEventSink.Outcome.SUCCEEDED,
                Duration.ofMillis(5),
                1,
                1,
                null,
                null,
                null,
                null,
                Instant.now());
    }
}
