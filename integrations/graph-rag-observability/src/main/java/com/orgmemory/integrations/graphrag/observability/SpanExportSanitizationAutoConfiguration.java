package com.orgmemory.integrations.graphrag.observability;

import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SpanExporters;
import org.springframework.context.annotation.Bean;

/**
 * Puts {@link ExceptionSanitizingSpanExporter} in front of every span exporter.
 *
 * <p>Spring Boot contributes its own {@code SpanExporters} bean only when one is missing, so
 * declaring this configuration ahead of it replaces the collection with a wrapped copy. Every
 * exporter is covered, including ones added later, and there is no toggle: a payload guard
 * that a deployment can switch off is not a guard.
 */
@AutoConfiguration(
        beforeName =
                "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure"
                        + ".OpenTelemetryTracingAutoConfiguration")
@ConditionalOnClass({SpanExporters.class, SpanExporter.class})
public class SpanExportSanitizationAutoConfiguration {

    @Bean
    SpanExporters spanExporters(ObjectProvider<SpanExporter> spanExporters) {
        return SpanExporters.of(spanExporters.orderedStream()
                .map(ExceptionSanitizingSpanExporter::new)
                .toList());
    }
}
