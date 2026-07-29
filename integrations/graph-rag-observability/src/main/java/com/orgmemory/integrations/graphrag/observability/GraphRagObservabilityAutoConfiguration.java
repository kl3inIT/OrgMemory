package com.orgmemory.integrations.graphrag.observability;

import com.orgmemory.graphrag.observability.GraphRagEventSink;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contributes the telemetry backends an application observes GraphRAG through.
 *
 * <p>Neither bean is {@code @ConditionalOnMissingBean(GraphRagEventSink.class)} any more. A
 * sink is not an exclusive port: producers fan one event out to every registered sink, and the
 * two here answer different questions — spans show one request end to end but are sampled,
 * meters are unsampled but aggregate. Making either back off because the other exists gave
 * whichever configuration class happened to run first the power to silence the other.
 *
 * <p>Each is individually switchable so a deployment can run one, both or neither. With none
 * enabled the producers compose to {@code NO_OP} and emission costs an empty loop.
 *
 * <p>Both registries are contributed by auto-configuration, so this class has to be ordered
 * after them for {@code @ConditionalOnBean} to see anything; a bean condition that runs too
 * early is not a safe default but a silent one.
 */
@AutoConfiguration(
        afterName = {
            "org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration",
            "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
            "org.springframework.boot.micrometer.metrics.autoconfigure"
                    + ".CompositeMeterRegistryAutoConfiguration"
        })
public class GraphRagObservabilityAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OpenTelemetry.class)
    @ConditionalOnProperty(
            prefix = "orgmemory.graph-rag.observability.opentelemetry",
            name = "enabled",
            matchIfMissing = true)
    static class OpenTelemetrySinkConfiguration {

        @Bean
        @ConditionalOnBean(OpenTelemetry.class)
        GraphRagEventSink openTelemetryGraphRagEventSink(OpenTelemetry openTelemetry) {
            return new OpenTelemetryGraphRagEventSink(openTelemetry);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(
            prefix = "orgmemory.graph-rag.observability.metrics",
            name = "enabled",
            matchIfMissing = true)
    static class MicrometerSinkConfiguration {

        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        GraphRagEventSink micrometerGraphRagEventSink(MeterRegistry meterRegistry) {
            return new MicrometerGraphRagEventSink(meterRegistry);
        }
    }
}
