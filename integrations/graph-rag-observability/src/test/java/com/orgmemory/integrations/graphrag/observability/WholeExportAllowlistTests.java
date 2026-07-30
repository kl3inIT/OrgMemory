package com.orgmemory.integrations.graphrag.observability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.observability.GraphRagEventSink;
import com.orgmemory.integrations.observability.ExceptionSanitizingSpanExporter;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The hardening runbook's release gate, executed.
 *
 * <p>The runbook says the export is limited to an allowlist and that the limit is structural.
 * Every other test here proves one adapter in isolation, which is a weaker claim: it says each
 * writer behaves, not that the bytes leaving the process contain nothing else. This walks the
 * whole exported span — name, attributes, status, every event and every event attribute,
 * instrumentation scope, and the resource — on both a success and a failure path, and fails on
 * anything not named.
 *
 * <p>It runs the sanitizing exporter in the position it occupies in production, so what is
 * asserted is the shape after the last gate rather than before it.
 */
class WholeExportAllowlistTests {

    private static final Set<String> ALLOWED_SPAN_ATTRIBUTES = Set.of(
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
            "orgmemory.graph_rag.failure_code",
            "orgmemory.graph_rag.prompt_tokens",
            "orgmemory.graph_rag.system_prompt_tokens",
            "orgmemory.graph_rag.query_tokens",
            "orgmemory.graph_rag.entity_tokens",
            "orgmemory.graph_rag.relation_tokens",
            "orgmemory.graph_rag.chunk_tokens",
            "orgmemory.graph_rag.budget_tokens",
            "orgmemory.graph_rag.dropped_contributions",
            "orgmemory.graph_rag.model_input_tokens",
            "orgmemory.graph_rag.model_output_tokens");

    /** Survives on an exception event; {@code ExceptionSanitizingSpanExporter} drops the rest. */
    private static final Set<String> ALLOWED_EVENT_ATTRIBUTES = Set.of("exception.type");

    /**
     * Text that would identify the leak if any of it reached an exporter. Each is a realistic
     * value for the field it stands in for rather than a marker string, because a marker only
     * proves the marker does not leak.
     */
    private static final List<String> FORBIDDEN_TEXT = List.of(
            "What is the parental leave policy",
            "Employees accrue 20 days",
            "confidential-handbook.pdf",
            "linh@example.com",
            "sk-live-",
            "OrgMemoryRetrievalException");

    @Test
    void nothingOutsideTheAllowlistLeavesTheProcessOnEitherPath() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        try (SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(
                        new ExceptionSanitizingSpanExporter(exporter)))
                .build()) {
            var sink = new OpenTelemetryGraphRagEventSink(
                    OpenTelemetrySdk.builder().setTracerProvider(provider).build());

            sink.emit(succeededWithEveryOptionalField());
            sink.emit(failedWithADiagnosticCode());

            List<SpanData> spans = exporter.getFinishedSpanItems();
            assertTrue(spans.size() == 2, "both paths must have exported");
            for (SpanData span : spans) {
                assertAllowed(span);
            }
        }
    }

    private static void assertAllowed(SpanData span) {
        assertTrue(
                span.getName().startsWith("orgmemory.graph_rag."),
                () -> "span name is not a stage name: " + span.getName());

        span.getAttributes().forEach((key, value) -> {
            assertTrue(
                    ALLOWED_SPAN_ATTRIBUTES.contains(key.getKey()),
                    () -> "attribute outside the allowlist reached the exporter: "
                            + key.getKey());
            assertNoForbiddenText(key.getKey(), value);
        });

        for (EventData event : span.getEvents()) {
            event.getAttributes().forEach((key, value) -> {
                assertTrue(
                        ALLOWED_EVENT_ATTRIBUTES.contains(key.getKey()),
                        () -> "event attribute outside the allowlist survived sanitization: "
                                + key.getKey());
                assertNoForbiddenText(key.getKey(), value);
            });
            assertNoForbiddenText("event.name", event.getName());
        }

        assertTrue(
                span.getStatus().getDescription().isEmpty(),
                () -> "the status description carries free text: "
                        + span.getStatus().getDescription());

        assertNoForbiddenText(
                "instrumentation scope",
                span.getInstrumentationScopeInfo().getName());
        span.getResource()
                .getAttributes()
                .forEach((key, value) -> assertNoForbiddenText(
                        "resource " + key.getKey(), value));
    }

    private static void assertNoForbiddenText(String where, Object value) {
        String rendered = String.valueOf(value).toLowerCase(Locale.ROOT);
        for (String forbidden : FORBIDDEN_TEXT) {
            assertFalse(
                    rendered.contains(forbidden.toLowerCase(Locale.ROOT)),
                    () -> where + " carries payload text: " + forbidden);
        }
    }

    @Test
    void theGateItselfFailsWhenSomethingUnmodelledIsExported() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        try (SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(
                        new ExceptionSanitizingSpanExporter(exporter)))
                .build()) {
            var tracer = OpenTelemetrySdk.builder()
                    .setTracerProvider(provider)
                    .build()
                    .getTracer(OpenTelemetryGraphRagEventSink.INSTRUMENTATION_SCOPE);
            tracer.spanBuilder("orgmemory.graph_rag.retrieve")
                    .startSpan()
                    .setAttribute(
                            AttributeKey.stringKey("orgmemory.graph_rag.question"),
                            "What is the parental leave policy")
                    .end();

            List<String> failures = new ArrayList<>();
            for (SpanData span : exporter.getFinishedSpanItems()) {
                try {
                    assertAllowed(span);
                } catch (AssertionError caught) {
                    failures.add(caught.getMessage());
                }
            }
            assertTrue(
                    failures.size() == 1,
                    "a gate that passes an unmodelled attribute is not a gate; got " + failures);
        }
    }

    private static GraphRagEventSink.GraphRagEvent succeededWithEveryOptionalField() {
        return new GraphRagEventSink.GraphRagEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                GraphRagEventSink.Stage.ASSEMBLE_CONTEXT,
                GraphRagEventSink.Outcome.SUCCEEDED,
                Duration.ofMillis(42),
                3,
                7,
                "a".repeat(64),
                "b".repeat(64),
                GraphRagEventSink.CacheStatus.HIT,
                null,
                new GraphRagEventSink.TokenUsage(1_400, 30, 12, 220, 180, 900, 29_800, 2),
                new GraphRagEventSink.ProviderTokenUsage(1_400, 260),
                Instant.parse("2026-07-30T09:00:00Z"));
    }

    private static GraphRagEventSink.GraphRagEvent failedWithADiagnosticCode() {
        return new GraphRagEventSink.GraphRagEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                GraphRagEventSink.Stage.RETRIEVE,
                GraphRagEventSink.Outcome.FAILED,
                Duration.ofMillis(8),
                1,
                0,
                null,
                null,
                null,
                "retrieval_unavailable",
                null,
                null,
                Instant.parse("2026-07-30T09:00:01Z"));
    }
}
