package com.orgmemory.graphrag.observability;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
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
