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
 *
 * <p>The same rule settles a question the design left open when it asked for a "tokens by
 * organization" board. Token counts are not tagged by organization here. One series per tenant
 * per channel grows without bound as the product sells, and the cost of an unbounded tag is paid
 * by the metrics backend forever rather than by the request that created it. Per-organization
 * attribution belongs to the span, which already carries the organization identifier, or to a
 * billing record, which is a product feature and not a side effect of telemetry.
 */
public final class MicrometerGraphRagEventSink implements GraphRagEventSink {

    static final String STAGE_TIMER = "orgmemory.graph_rag.stage";
    static final String FAILURE_COUNTER = "orgmemory.graph_rag.stage.failures";
    static final String INPUT_COUNTER = "orgmemory.graph_rag.stage.inputs";
    static final String OUTPUT_COUNTER = "orgmemory.graph_rag.stage.outputs";
    static final String CONTEXT_TOKEN_COUNTER = "orgmemory.graph_rag.context.tokens";
    static final String CONTEXT_PROMPT_TOKENS = "orgmemory.graph_rag.context.prompt_tokens";
    static final String CONTEXT_DROPPED_COUNTER =
            "orgmemory.graph_rag.context.dropped_contributions";
    static final String CONTEXT_TRUNCATION_COUNTER =
            "orgmemory.graph_rag.context.truncations";
    static final String MODEL_TOKEN_COUNTER = "orgmemory.graph_rag.model.tokens";

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

        if (event.tokenUsage() != null) {
            recordTokenUsage(event.tokenUsage());
        }

        // Tagged by stage because that is a closed enum, and by direction because input and
        // output tokens are priced differently by every provider — summing them into one number
        // makes the meter unable to answer the only question it exists for.
        if (event.providerTokens() != null) {
            countModelTokens(event.stage(), "input", event.providerTokens().inputTokens());
            countModelTokens(event.stage(), "output", event.providerTokens().outputTokens());
        }
    }

    private void countModelTokens(Stage stage, String direction, int tokens) {
        registry.counter(
                        MODEL_TOKEN_COUNTER,
                        Tags.of("stage", enumValue(stage), "direction", direction))
                .increment(tokens);
    }

    private void recordTokenUsage(TokenUsage usage) {
        countTokens("system_prompt", usage.systemPromptTokens());
        countTokens("query", usage.queryTokens());
        countTokens("entity", usage.entityTokens());
        countTokens("relation", usage.relationTokens());
        countTokens("chunk", usage.chunkTokens());
        // Summarised rather than counted, unlike the channels above: the channel totals answer
        // what the deployment spends, while the size of one assembled prompt is a per-request
        // shape, and a p95 close to the budget is the warning that arrives before truncation
        // starts rather than after.
        registry.summary(CONTEXT_PROMPT_TOKENS).record(usage.promptTokens());

        // Two meters because they answer two questions a single one cannot. The dropped counter
        // says how much context the budget refused; the truncation counter says how many answers
        // were affected at all, which no sum of dropped items can recover.
        if (usage.truncated()) {
            registry.counter(CONTEXT_DROPPED_COUNTER)
                    .increment(usage.droppedContributions());
            registry.counter(CONTEXT_TRUNCATION_COUNTER).increment();
        }
    }

    private void countTokens(String channel, int tokens) {
        registry.counter(CONTEXT_TOKEN_COUNTER, "channel", channel).increment(tokens);
    }

    private static String enumValue(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "MicrometerGraphRagEventSink{" + registry.getClass().getSimpleName() + "}";
    }
}
