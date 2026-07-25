package com.orgmemory.integrations.graphrag.observability;

import com.orgmemory.graphrag.observability.GraphRagEventSink;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenTelemetry.class)
@ConditionalOnBean(OpenTelemetry.class)
public class GraphRagObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GraphRagEventSink.class)
    GraphRagEventSink graphRagEventSink(OpenTelemetry openTelemetry) {
        return new OpenTelemetryGraphRagEventSink(openTelemetry);
    }
}
