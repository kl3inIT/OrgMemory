package com.orgmemory.graphrag.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GraphRagEventSinkTests {

    @Test
    void rejectsRouteTextThatIsNotAnOpaqueFingerprint() {
        assertThrows(IllegalArgumentException.class, () -> event(
                GraphRagEventSink.Outcome.SUCCEEDED,
                "openai:gpt-model:prompt-v1",
                null));
    }

    @Test
    void rejectsUnboundedFailureDiagnostics() {
        assertThrows(IllegalArgumentException.class, () -> event(
                GraphRagEventSink.Outcome.FAILED,
                null,
                "provider failed: raw payload"));
    }

    @Test
    void deliversOneEventToEveryConfiguredBackend() {
        var otel = new RecordingSink();
        var meters = new RecordingSink();

        GraphRagEventSink.composite(List.of(otel, meters))
                .emit(event(GraphRagEventSink.Outcome.SUCCEEDED, null, null));

        assertEquals(1, otel.received.size());
        assertEquals(1, meters.received.size());
    }

    @Test
    void oneFailingBackendDoesNotSilenceTheOthers() {
        var failing = new RecordingSink(new IllegalStateException("collector down"));
        var healthy = new RecordingSink();

        GraphRagEventSink composite = GraphRagEventSink.composite(List.of(failing, healthy));
        var event = event(GraphRagEventSink.Outcome.SUCCEEDED, null, null);

        assertThrows(IllegalStateException.class, () -> composite.emit(event));
        assertEquals(1, healthy.received.size(), "a broken backend must not drop the rest");
    }

    @Test
    void reportsEveryFailureRatherThanOnlyTheFirst() {
        var firstFailure = new IllegalStateException("first");
        var secondFailure = new IllegalStateException("second");

        GraphRagEventSink composite = GraphRagEventSink.composite(
                List.of(new RecordingSink(firstFailure), new RecordingSink(secondFailure)));
        var event = event(GraphRagEventSink.Outcome.SUCCEEDED, null, null);

        RuntimeException thrown =
                assertThrows(IllegalStateException.class, () -> composite.emit(event));

        assertSame(firstFailure, thrown, "the first failure is the one that propagates");
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(secondFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void survivesTwoBackendsRaisingOneSharedFailure() {
        var shared = new IllegalStateException("collector down");

        GraphRagEventSink composite = GraphRagEventSink.composite(
                List.of(new RecordingSink(shared), new RecordingSink(shared)));
        var event = event(GraphRagEventSink.Outcome.SUCCEEDED, null, null);

        RuntimeException thrown =
                assertThrows(IllegalStateException.class, () -> composite.emit(event));

        assertSame(shared, thrown);
        assertEquals(
                0,
                thrown.getSuppressed().length,
                "Throwable.addSuppressed rejects self-suppression, so it must not be called");
    }

    @Test
    void anApplicationWithoutBackendsEmitsNothingRatherThanFailing() {
        assertSame(GraphRagEventSink.NO_OP, GraphRagEventSink.composite(List.of()));
    }

    @Test
    void doesNotWrapASingleBackend() {
        var only = new RecordingSink();
        assertSame(only, GraphRagEventSink.composite(List.of(only)));
    }

    private static final class RecordingSink implements GraphRagEventSink {

        private final List<GraphRagEvent> received = new ArrayList<>();
        private final RuntimeException failure;

        private RecordingSink() {
            this(null);
        }

        private RecordingSink(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void emit(GraphRagEvent event) {
            if (failure != null) {
                throw failure;
            }
            received.add(event);
        }
    }

    private static GraphRagEventSink.GraphRagEvent event(
            GraphRagEventSink.Outcome outcome,
            String routeFingerprint,
            String failureCode) {
        return new GraphRagEventSink.GraphRagEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                GraphRagEventSink.Stage.RETRIEVE,
                outcome,
                Duration.ofMillis(12),
                1,
                2,
                routeFingerprint,
                null,
                null,
                failureCode,
                Instant.now());
    }
}
