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

    /**
     * Context assembly adds attributes named after the channels this boundary refuses to carry —
     * {@code query_tokens}, {@code system_prompt_tokens}. The names are not the danger and a
     * substring check on them would be the wrong guard: what makes a count safe is that a number
     * cannot reconstruct the text it measured. So this asserts the exact key set and then asserts
     * every added key is numeric, which no amount of prompt text could satisfy.
     */
    @Test
    void carriesTheContextTokenBreakdownAsNumbersThatCannotHoldTheTextTheyMeasure() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        try (SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()) {
            var telemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(provider)
                    .build();
            var sink = new OpenTelemetryGraphRagEventSink(telemetry);

            sink.emit(new GraphRagEventSink.GraphRagEvent(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    GraphRagEventSink.Stage.ASSEMBLE_CONTEXT,
                    GraphRagEventSink.Outcome.SUCCEEDED,
                    Duration.ofMillis(12),
                    2,
                    7,
                    null,
                    null,
                    null,
                    null,
                    new GraphRagEventSink.TokenUsage(
                            1_400, 30, 12, 220, 180, 900, 29_800, 3),
                    null,
                    Instant.parse("2026-07-30T09:00:00Z")));

            var span = exporter.getFinishedSpanItems().getFirst();
            assertEquals(Set.of(
                    "orgmemory.graph_rag.operation_id",
                    "orgmemory.graph_rag.organization_id",
                    "orgmemory.graph_rag.stage",
                    "orgmemory.graph_rag.outcome",
                    "orgmemory.graph_rag.duration_nanos",
                    "orgmemory.graph_rag.input_count",
                    "orgmemory.graph_rag.output_count",
                    "orgmemory.graph_rag.prompt_tokens",
                    "orgmemory.graph_rag.system_prompt_tokens",
                    "orgmemory.graph_rag.query_tokens",
                    "orgmemory.graph_rag.entity_tokens",
                    "orgmemory.graph_rag.relation_tokens",
                    "orgmemory.graph_rag.chunk_tokens",
                    "orgmemory.graph_rag.budget_tokens",
                    "orgmemory.graph_rag.dropped_contributions"),
                    span.getAttributes().asMap().keySet().stream()
                            .map(io.opentelemetry.api.common.AttributeKey::getKey)
                            .collect(Collectors.toSet()));
            span.getAttributes().forEach((key, value) -> {
                if (key.getKey().endsWith("_tokens")
                        || key.getKey().endsWith("dropped_contributions")) {
                    assertEquals(
                            io.opentelemetry.api.common.AttributeType.LONG,
                            key.getType(),
                            () -> key.getKey() + " must be a count, not text");
                }
            });
            assertEquals(1_400L, span.getAttributes().get(
                    OpenTelemetryGraphRagEventSink.PROMPT_TOKENS));
            assertEquals(3L, span.getAttributes().get(
                    OpenTelemetryGraphRagEventSink.DROPPED_CONTRIBUTIONS));
        }
    }

    @Test
    void omitsTheTokenAttributesForAStageThatMeasuresNone() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        try (SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()) {
            var telemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(provider)
                    .build();
            new OpenTelemetryGraphRagEventSink(telemetry).emit(
                    new GraphRagEventSink.GraphRagEvent(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            GraphRagEventSink.Stage.EMBED,
                            GraphRagEventSink.Outcome.SUCCEEDED,
                            Duration.ofMillis(4),
                            1,
                            1,
                            null,
                            null,
                            null,
                            null,
                            Instant.parse("2026-07-30T09:00:00Z")));

            var span = exporter.getFinishedSpanItems().getFirst();
            assertFalse(
                    span.getAttributes().asMap().keySet().stream()
                            .anyMatch(key -> key.getKey().endsWith("_tokens")),
                    "a zero token count would read as a measured zero rather than as no measurement");
        }
    }

    private static long epochNanos(Instant instant) {
        return Math.addExact(
                Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L),
                instant.getNano());
    }
}
