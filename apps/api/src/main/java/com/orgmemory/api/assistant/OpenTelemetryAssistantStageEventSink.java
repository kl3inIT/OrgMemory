package com.orgmemory.api.assistant;

import com.orgmemory.core.assistant.observability.AssistantStageEventSink;
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

/** Payload-free OpenTelemetry adapter for assistant latency attribution. */
final class OpenTelemetryAssistantStageEventSink
        implements AssistantStageEventSink {

    static final String INSTRUMENTATION_SCOPE =
            "com.orgmemory.assistant";
    static final AttributeKey<String> ENGINE =
            AttributeKey.stringKey("orgmemory.assistant.engine");
    static final AttributeKey<String> STAGE =
            AttributeKey.stringKey("orgmemory.assistant.stage");
    static final AttributeKey<String> OUTCOME =
            AttributeKey.stringKey("orgmemory.assistant.outcome");
    static final AttributeKey<Long> DURATION_NANOS =
            AttributeKey.longKey("orgmemory.assistant.duration_nanos");
    static final AttributeKey<String> FAILURE_CODE =
            AttributeKey.stringKey("orgmemory.assistant.failure_code");

    private final Tracer tracer;

    OpenTelemetryAssistantStageEventSink(OpenTelemetry openTelemetry) {
        tracer = Objects.requireNonNull(openTelemetry, "openTelemetry")
                .getTracer(INSTRUMENTATION_SCOPE);
    }

    @Override
    public void emit(AssistantStageEvent event) {
        Objects.requireNonNull(event, "event");
        long endEpochNanos = epochNanos(event.occurredAt());
        long startEpochNanos = Math.subtractExact(
                endEpochNanos,
                event.duration().toNanos());
        Span span = tracer.spanBuilder(
                        "orgmemory.assistant." + value(event.stage()))
                .setSpanKind(SpanKind.INTERNAL)
                .setStartTimestamp(
                        startEpochNanos,
                        TimeUnit.NANOSECONDS)
                .startSpan();
        span.setAttribute(ENGINE, value(event.engine()));
        span.setAttribute(STAGE, value(event.stage()));
        span.setAttribute(OUTCOME, value(event.outcome()));
        span.setAttribute(
                DURATION_NANOS,
                event.duration().toNanos());
        if (event.failureCode() != null) {
            span.setAttribute(FAILURE_CODE, event.failureCode());
        }
        if (event.outcome() == Outcome.FAILED) {
            span.setStatus(StatusCode.ERROR);
        }
        span.end(endEpochNanos, TimeUnit.NANOSECONDS);
    }

    private static String value(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static long epochNanos(Instant instant) {
        return Math.addExact(
                Math.multiplyExact(
                        instant.getEpochSecond(),
                        1_000_000_000L),
                instant.getNano());
    }
}
