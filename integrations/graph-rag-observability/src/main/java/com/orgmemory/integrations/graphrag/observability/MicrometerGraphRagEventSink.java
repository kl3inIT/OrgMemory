package com.orgmemory.integrations.graphrag.observability;

import com.orgmemory.graphrag.observability.GraphRagEventSink;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Payload-free Micrometer adapter for completed GraphRAG stages.
 *
 * <p>Traces are sampled, so span data answers "what happened in this request" but not "what is
 * the p95 of the embed stage" — at a tenth of requests the tail is mostly missing. Metrics are
 * not sampled, so the same events aggregated here answer the latency question at full
 * coverage. The two sinks are complementary rather than alternatives, which is why
 * {@link GraphRagObservabilityAutoConfiguration} no longer treats a sink as an exclusive port.
 *
 * <p>Tags are deliberately restricted to bounded enumerations. Every dimension here multiplies
 * the number of stored series, so an unbounded one is not a richer metric but a broken one:
 * organization and operation identifiers, fingerprints and durations stay out. They remain on
 * the span, where each is one record rather than a permanent series. That is also why
 * {@code failureCode} — bounded by the port's own {@code [a-z0-9_]{1,64}} contract but not by a
 * closed enum — tags only the failure counter and never the timer.
 */
public final class MicrometerGraphRagEventSink implements GraphRagEventSink {

    static final String STAGE_TIMER = "orgmemory.graph_rag.stage";
    static final String FAILURE_COUNTER = "orgmemory.graph_rag.stage.failures";
    static final String INPUT_COUNTER = "orgmemory.graph_rag.stage.inputs";
    static final String OUTPUT_COUNTER = "orgmemory.graph_rag.stage.outputs";

    private final MeterRegistry registry;

    public MicrometerGraphRagEventSink(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void emit(GraphRagEvent event) {
        Objects.requireNonNull(event, "event");
        Tags tags = Tags.of(
                "stage", enumValue(event.stage()),
                "outcome", enumValue(event.outcome()),
                "cache_status", event.cacheStatus() == null ? "none" : enumValue(event.cacheStatus()));

        registry.timer(STAGE_TIMER, tags)
                .record(event.duration().toNanos(), TimeUnit.NANOSECONDS);
        // Counted rather than summarised: the interesting questions are how much work a stage
        // consumed and produced in total, which a rate over a counter answers and a
        // distribution over per-event values does not.
        registry.counter(INPUT_COUNTER, tags).increment(event.inputCount());
        registry.counter(OUTPUT_COUNTER, tags).increment(event.outputCount());

        if (event.failureCode() != null) {
            registry.counter(
                            FAILURE_COUNTER,
                            Tags.of(
                                    "stage", enumValue(event.stage()),
                                    "failure_code", event.failureCode()))
                    .increment();
        }
    }

    private static String enumValue(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "MicrometerGraphRagEventSink{" + registry.getClass().getSimpleName() + "}";
    }
}
