package com.orgmemory.integrations.graphrag.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.observability.GraphRagEventSink;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A sink used to be {@code @ConditionalOnMissingBean(GraphRagEventSink.class)}, which meant the
 * first backend to be configured silently prevented the second. Since producers fan one event
 * out to every sink, and spans and meters answer different questions, that was a defect rather
 * than a safeguard. These tests hold the corrected wiring in place.
 */
class GraphRagObservabilityAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GraphRagObservabilityAutoConfiguration.class))
            .withUserConfiguration(BackendConfiguration.class);

    @Test
    void staysDiscoverableWithoutAnApplicationNamingIt() {
        List<String> names = new ArrayList<>();
        ImportCandidates.load(
                        AutoConfiguration.class,
                        GraphRagObservabilityAutoConfigurationTests.class.getClassLoader())
                .forEach(names::add);

        assertTrue(names.contains(GraphRagObservabilityAutoConfiguration.class.getName()));
    }

    @Test
    void contributesBothBackendsBecauseNeitherAnswersTheOthersQuestion() {
        runner.run(context -> {
            Map<String, GraphRagEventSink> sinks = context.getBeansOfType(GraphRagEventSink.class);

            assertEquals(2, sinks.size(), "one backend must not displace the other");
            assertTrue(sinks.containsKey("openTelemetryGraphRagEventSink"));
            assertTrue(sinks.containsKey("micrometerGraphRagEventSink"));
        });
    }

    @Test
    void leavesOnlyTheSpanBackendWhenMetricsAreTurnedOff() {
        runner.withPropertyValues("orgmemory.graph-rag.observability.metrics.enabled=false")
                .run(context -> assertEquals(
                        List.of("openTelemetryGraphRagEventSink"),
                        List.copyOf(context.getBeansOfType(GraphRagEventSink.class).keySet())));
    }

    @Test
    void leavesOnlyTheMeterBackendWhenSpansAreTurnedOff() {
        runner.withPropertyValues("orgmemory.graph-rag.observability.opentelemetry.enabled=false")
                .run(context -> assertEquals(
                        List.of("micrometerGraphRagEventSink"),
                        List.copyOf(context.getBeansOfType(GraphRagEventSink.class).keySet())));
    }

    @Test
    void composesToNoOpWhenADeploymentWantsNoTelemetryAtAll() {
        runner.withPropertyValues(
                        "orgmemory.graph-rag.observability.opentelemetry.enabled=false",
                        "orgmemory.graph-rag.observability.metrics.enabled=false")
                .run(context -> {
                    List<GraphRagEventSink> sinks =
                            List.copyOf(context.getBeansOfType(GraphRagEventSink.class).values());

                    assertTrue(sinks.isEmpty());
                    assertSame(
                            GraphRagEventSink.NO_OP,
                            GraphRagEventSink.composite(sinks),
                            "emission must cost nothing rather than fail when nothing observes it");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class BackendConfiguration {

        @Bean
        OpenTelemetry openTelemetry() {
            return OpenTelemetry.noop();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
