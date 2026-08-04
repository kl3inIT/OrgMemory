package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.orgmemory.core.assistant.observability.AssistantStageEventSink;
import com.orgmemory.core.assistant.observability.AssistantTurnEvent;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OpenTelemetryAssistantStageEventSinkTests {

    @Test
    void exportsOnlyTheClosedPayloadFreeAttributeSetWithOriginalTiming() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        try (SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()) {
            OpenTelemetrySdk telemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(provider)
                    .build();
            var sink = new OpenTelemetryAssistantStageEventSink(telemetry);
            Instant endedAt =
                    Instant.parse("2026-08-05T01:02:03.123456789Z");
            Duration duration = Duration.ofMillis(125);

            sink.emit(new AssistantStageEventSink.AssistantStageEvent(
                    AssistantTurnEvent.RetrievalEngine.GRAPH_RAG,
                    AssistantStageEventSink.Stage
                            .CONVERSATION_HISTORY_LOAD,
                    AssistantStageEventSink.Outcome.FAILED,
                    duration,
                    "history_load_failed",
                    endedAt));

            var span = exporter.getFinishedSpanItems().getFirst();
            assertEquals(
                    "orgmemory.assistant.conversation_history_load",
                    span.getName());
            assertEquals(
                    StatusCode.ERROR,
                    span.getStatus().getStatusCode());
            assertEquals(
                    epochNanos(endedAt),
                    span.getEndEpochNanos());
            assertEquals(
                    duration.toNanos(),
                    span.getEndEpochNanos()
                            - span.getStartEpochNanos());
            Set<String> keys = span.getAttributes().asMap().keySet()
                    .stream()
                    .map(key -> key.getKey())
                    .collect(Collectors.toSet());
            assertEquals(
                    Set.of(
                            "orgmemory.assistant.engine",
                            "orgmemory.assistant.stage",
                            "orgmemory.assistant.outcome",
                            "orgmemory.assistant.duration_nanos",
                            "orgmemory.assistant.failure_code"),
                    keys);
            assertFalse(keys.stream().anyMatch(key ->
                    key.contains("query")
                            || key.contains("prompt")
                            || key.contains("evidence")
                            || key.contains("user")
                            || key.contains("conversation")
                            || key.contains("exception")));
        }
    }

    private static long epochNanos(Instant instant) {
        return Math.addExact(
                Math.multiplyExact(
                        instant.getEpochSecond(),
                        1_000_000_000L),
                instant.getNano());
    }
}
