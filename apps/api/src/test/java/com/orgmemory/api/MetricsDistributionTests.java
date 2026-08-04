package com.orgmemory.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.PropertiesMeterFilter;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Every latency panel on every board asks for a quantile, and a Micrometer timer answers with
 * one {@code +Inf} bucket unless it is told to publish a histogram.
 *
 * <p>That combination fails without failing: the count and the sum are exported, the panel
 * names a metric that genuinely exists, and {@code histogram_quantile} quietly returns NaN, so
 * the chart is empty forever and looks like an idle system. Production ran in exactly that
 * state — twenty-eight histogram families, none of them with a usable bucket — while every
 * other check passed.
 *
 * <p>Reading the real {@code application.yml} through the real {@link PropertiesMeterFilter} is
 * the point: the property is a prefix map where the longest key wins, so a test that restated
 * the mapping would agree with itself rather than with what the application configures.
 */
class MetricsDistributionTests {

    private final MeterFilter filter = new PropertiesMeterFilter(bindMetricsProperties());

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http.server.requests",
                "jvm.gc.pause",
                "orgmemory.assistant.turn",
                "orgmemory.assistant.time_to_first_token",
                "orgmemory.assistant.stage",
                "orgmemory.graph_rag.stage",
                "gen_ai.client.operation"
            })
    void meterChartedAsAQuantilePublishesAHistogram(String name) {
        DistributionStatisticConfig config = configure(name);

        assertEquals(
                Boolean.TRUE,
                config.isPercentileHistogram(),
                () -> name + " is charted with histogram_quantile but publishes no buckets, "
                        + "so the panel is silently empty");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http.server.requests",
                "orgmemory.assistant.turn",
                "orgmemory.assistant.stage",
                "orgmemory.graph_rag.stage",
                "gen_ai.client.operation"
            })
    void histogramRangeIsBoundedSoBucketsDoNotMultiplySeries(String name) {
        DistributionStatisticConfig config = configure(name);

        assertTrue(
                config.getMinimumExpectedValueAsDouble() != null
                        && config.getMaximumExpectedValueAsDouble() != null,
                () -> name + " publishes a histogram over an unbounded range, and bucket count "
                        + "multiplies every tag combination it already carries");
    }

    @Test
    void assistantLatencyRangeReachesAFullLlmTurn() {
        DistributionStatisticConfig config = configure("orgmemory.assistant.turn");

        assertTrue(
                config.getMaximumExpectedValueAsDouble() >= 60_000_000_000.0,
                "an assistant turn is answered by an LLM over permission-scoped retrieval, so a "
                        + "ceiling under a minute puts ordinary answers in the overflow bucket");
    }

    /**
     * {@code /api/assistant/chat} is an HTTP request that streams for the whole turn, so the
     * HTTP ceiling has to cover an assistant turn rather than an ordinary request. Set below
     * it, every real chat lands in the overflow bucket and HTTP p95 reports exactly the bound —
     * a flat plateau at a suspiciously round number that no request ever took.
     */
    @Test
    void httpCeilingCoversTheStreamingAssistantTurnItServes() {
        Double http = configure("http.server.requests").getMaximumExpectedValueAsDouble();
        Double turn = configure("orgmemory.assistant.turn").getMaximumExpectedValueAsDouble();

        assertTrue(
                http >= turn,
                () -> "HTTP requests are bounded at " + http + "ns while the assistant turn "
                        + "served over one is bounded at " + turn + "ns, so every streamed chat "
                        + "overflows and pins HTTP p95 to its own ceiling");
    }

    private DistributionStatisticConfig configure(String name) {
        Meter.Id id = new SimpleMeterRegistry().timer(name).getId();
        return filter.configure(id, DistributionStatisticConfig.DEFAULT);
    }

    private static MetricsProperties bindMetricsProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"));
            sources.forEach(environment.getPropertySources()::addFirst);
        } catch (IOException unreadable) {
            throw new IllegalStateException("application.yml is not on the test classpath",
                    unreadable);
        }
        return Binder.get(environment)
                .bind("management.metrics", MetricsProperties.class)
                .orElseThrow(() -> new IllegalStateException(
                        "management.metrics is absent, so no meter publishes a histogram"));
    }
}
