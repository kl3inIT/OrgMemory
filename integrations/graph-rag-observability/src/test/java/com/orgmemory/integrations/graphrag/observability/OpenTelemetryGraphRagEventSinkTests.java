package com.orgmemory.integrations.graphrag.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.orgmemory.graphrag.observability.GraphRagEventSink;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OpenTelemetryGraphRagEventSinkTests {

    @Test
    void exportsOnlyTheClosedPayloadFreeAttributeSetWithOriginalTiming() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        try (SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()) {
            var telemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(provider)
                    .build();
            var sink = new OpenTelemetryGraphRagEventSink(telemetry);
            UUID operationId = UUID.randomUUID();
            UUID organizationId = UUID.randomUUID();
            Instant endedAt = Instant.parse("2026-07-24T10:15:30.123456789Z");
            Duration duration = Duration.ofMillis(125);

            sink.emit(new GraphRagEventSink.GraphRagEvent(
                    operationId,
                    organizationId,
                    GraphRagEventSink.Stage.RETRIEVE,
                    GraphRagEventSink.Outcome.FAILED,
                    duration,
                    4,
                    2,
                    "a".repeat(64),
                    "b".repeat(64),
                    GraphRagEventSink.CacheStatus.HIT,
                    "retrieval_failed",
                    endedAt));

            var span = exporter.getFinishedSpanItems().getFirst();
            assertEquals("orgmemory.graph_rag.retrieve", span.getName());
            assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
            assertEquals(epochNanos(endedAt), span.getEndEpochNanos());
            assertEquals(duration.toNanos(), span.getEndEpochNanos() - span.getStartEpochNanos());
            assertEquals(operationId.toString(), span.getAttributes().get(
                    OpenTelemetryGraphRagEventSink.OPERATION_ID));
            assertEquals(organizationId.toString(), span.getAttributes().get(
                    OpenTelemetryGraphRagEventSink.ORGANIZATION_ID));

            Set<String> keys = span.getAttributes().asMap().keySet().stream()
                    .map(key -> key.getKey())
                    .collect(Collectors.toSet());
            assertEquals(Set.of(
                    "orgmemory.graph_rag.operation_id",
                    "orgmemory.graph_rag.organization_id",
                    "orgmemory.graph_rag.stage",
                    "orgmemory.graph_rag.outcome",
                    "orgmemory.graph_rag.duration_nanos",
                    "orgmemory.graph_rag.input_count",
                    "orgmemory.graph_rag.output_count",
                    "orgmemory.graph_rag.model_route_fingerprint",
                    "orgmemory.graph_rag.scope_fingerprint",
                    "orgmemory.graph_rag.cache_status",
                    "orgmemory.graph_rag.failure_code"), keys);
            assertFalse(keys.stream().anyMatch(key ->
                    key.contains("query")
                            || key.contains("prompt")
                            || key.contains("evidence")
                            || key.contains("user")
                            || key.contains("exception")));
        }
    }

    private static long epochNanos(Instant instant) {
        return Math.addExact(
                Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L),
                instant.getNano());
    }
}
