package com.orgmemory.integrations.graphrag.observability;

import com.orgmemory.graphrag.observability.GraphRagEventSink;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Payload-free OpenTelemetry adapter for completed GraphRAG stages.
 *
 * <p>The attribute set is deliberately closed. Prompt, query, completion,
 * evidence, document, actor, ACL and exception data cannot enter this adapter.
 */
public final class OpenTelemetryGraphRagEventSink implements GraphRagEventSink {

    static final String INSTRUMENTATION_SCOPE = "com.orgmemory.graph-rag";
    static final AttributeKey<String> OPERATION_ID =
            AttributeKey.stringKey("orgmemory.graph_rag.operation_id");
    static final AttributeKey<String> ORGANIZATION_ID =
            AttributeKey.stringKey("orgmemory.graph_rag.organization_id");
    static final AttributeKey<String> STAGE =
            AttributeKey.stringKey("orgmemory.graph_rag.stage");
    static final AttributeKey<String> OUTCOME =
            AttributeKey.stringKey("orgmemory.graph_rag.outcome");
    static final AttributeKey<Long> DURATION_NANOS =
            AttributeKey.longKey("orgmemory.graph_rag.duration_nanos");
    static final AttributeKey<Long> INPUT_COUNT =
            AttributeKey.longKey("orgmemory.graph_rag.input_count");
    static final AttributeKey<Long> OUTPUT_COUNT =
            AttributeKey.longKey("orgmemory.graph_rag.output_count");
    static final AttributeKey<String> MODEL_ROUTE_FINGERPRINT =
            AttributeKey.stringKey("orgmemory.graph_rag.model_route_fingerprint");
    static final AttributeKey<String> SCOPE_FINGERPRINT =
            AttributeKey.stringKey("orgmemory.graph_rag.scope_fingerprint");
    static final AttributeKey<String> CACHE_STATUS =
            AttributeKey.stringKey("orgmemory.graph_rag.cache_status");
    static final AttributeKey<String> FAILURE_CODE =
            AttributeKey.stringKey("orgmemory.graph_rag.failure_code");

    private final Tracer tracer;

    public OpenTelemetryGraphRagEventSink(OpenTelemetry openTelemetry) {
        this.tracer = Objects.requireNonNull(openTelemetry, "openTelemetry")
                .getTracer(INSTRUMENTATION_SCOPE);
    }

    @Override
    public void emit(GraphRagEvent event) {
        Objects.requireNonNull(event, "event");
        long endEpochNanos = epochNanos(event.occurredAt());
        long startEpochNanos = Math.subtractExact(
                endEpochNanos,
                event.duration().toNanos());
        Span span = tracer.spanBuilder(spanName(event))
                .setSpanKind(SpanKind.INTERNAL)
                .setStartTimestamp(startEpochNanos, TimeUnit.NANOSECONDS)
                .startSpan();
        span.setAttribute(OPERATION_ID, event.operationId().toString());
        span.setAttribute(ORGANIZATION_ID, event.organizationId().toString());
        span.setAttribute(STAGE, enumValue(event.stage()));
        span.setAttribute(OUTCOME, enumValue(event.outcome()));
        span.setAttribute(DURATION_NANOS, event.duration().toNanos());
        span.setAttribute(INPUT_COUNT, event.inputCount());
        span.setAttribute(OUTPUT_COUNT, event.outputCount());
        if (event.modelRouteFingerprint() != null) {
            span.setAttribute(
                    MODEL_ROUTE_FINGERPRINT,
                    event.modelRouteFingerprint());
        }
        if (event.scopeFingerprint() != null) {
            span.setAttribute(
                    SCOPE_FINGERPRINT,
                    event.scopeFingerprint());
        }
        if (event.cacheStatus() != null) {
            span.setAttribute(CACHE_STATUS, enumValue(event.cacheStatus()));
        }
        if (event.failureCode() != null) {
            span.setAttribute(FAILURE_CODE, event.failureCode());
        }
        if (event.outcome() == Outcome.FAILED) {
            span.setStatus(StatusCode.ERROR);
        }
        span.end(endEpochNanos, TimeUnit.NANOSECONDS);
    }

    private static String spanName(GraphRagEvent event) {
        return "orgmemory.graph_rag." + enumValue(event.stage());
    }

    private static String enumValue(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static long epochNanos(Instant instant) {
        return Math.addExact(
                Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L),
                instant.getNano());
    }
}
