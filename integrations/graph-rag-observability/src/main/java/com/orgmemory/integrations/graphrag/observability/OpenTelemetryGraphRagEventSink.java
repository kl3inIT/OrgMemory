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
    static final AttributeKey<Long> PROMPT_TOKENS =
            AttributeKey.longKey("orgmemory.graph_rag.prompt_tokens");
    static final AttributeKey<Long> SYSTEM_PROMPT_TOKENS =
            AttributeKey.longKey("orgmemory.graph_rag.system_prompt_tokens");
    static final AttributeKey<Long> QUERY_TOKENS =
            AttributeKey.longKey("orgmemory.graph_rag.query_tokens");
    static final AttributeKey<Long> ENTITY_TOKENS =
            AttributeKey.longKey("orgmemory.graph_rag.entity_tokens");
    static final AttributeKey<Long> RELATION_TOKENS =
            AttributeKey.longKey("orgmemory.graph_rag.relation_tokens");
    static final AttributeKey<Long> CHUNK_TOKENS =
            AttributeKey.longKey("orgmemory.graph_rag.chunk_tokens");
    static final AttributeKey<Long> BUDGET_TOKENS =
            AttributeKey.longKey("orgmemory.graph_rag.budget_tokens");
    static final AttributeKey<Long> DROPPED_CONTRIBUTIONS =
            AttributeKey.longKey("orgmemory.graph_rag.dropped_contributions");
    static final AttributeKey<Long> MODEL_INPUT_TOKENS =
            AttributeKey.longKey("orgmemory.graph_rag.model_input_tokens");
    static final AttributeKey<Long> MODEL_OUTPUT_TOKENS =
            AttributeKey.longKey("orgmemory.graph_rag.model_output_tokens");

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
        if (event.tokenUsage() != null) {
            recordTokenUsage(span, event.tokenUsage());
        }
        if (event.providerTokens() != null) {
            span.setAttribute(MODEL_INPUT_TOKENS, event.providerTokens().inputTokens());
            span.setAttribute(MODEL_OUTPUT_TOKENS, event.providerTokens().outputTokens());
        }
        if (event.outcome() == Outcome.FAILED) {
            span.setStatus(StatusCode.ERROR);
        }
        span.end(endEpochNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * The span carries the whole breakdown because a span is one record rather
     * than a permanent series, so the per-request detail that would be reckless
     * as metric dimensions is affordable here.
     */
    private static void recordTokenUsage(Span span, TokenUsage usage) {
        span.setAttribute(PROMPT_TOKENS, usage.promptTokens());
        span.setAttribute(SYSTEM_PROMPT_TOKENS, usage.systemPromptTokens());
        span.setAttribute(QUERY_TOKENS, usage.queryTokens());
        span.setAttribute(ENTITY_TOKENS, usage.entityTokens());
        span.setAttribute(RELATION_TOKENS, usage.relationTokens());
        span.setAttribute(CHUNK_TOKENS, usage.chunkTokens());
        span.setAttribute(BUDGET_TOKENS, usage.budgetTokens());
        span.setAttribute(DROPPED_CONTRIBUTIONS, usage.droppedContributions());
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
