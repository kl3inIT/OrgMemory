package com.orgmemory.integrations.graphrag.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SpanExporters;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The sanitizer only protects anything if it is actually in the export path. Spring Boot
 * contributes its own unwrapped {@code SpanExporters} whenever one is missing, so the two
 * questions are whether this module is discovered at all and whether it is ordered ahead of
 * Boot's copy.
 */
class SpanExportSanitizationAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpanExportSanitizationAutoConfiguration.class))
            .withUserConfiguration(ExporterConfiguration.class);

    @Test
    void staysDiscoverableWithoutAnApplicationNamingIt() {
        assertTrue(
                registeredAutoConfigurations()
                        .contains(SpanExportSanitizationAutoConfiguration.class.getName()),
                "META-INF/spring/…AutoConfiguration.imports no longer names this class, so spans "
                        + "would reach the collector with their exception messages intact");
    }

    @Test
    void wrapsEveryExporterTheApplicationDeclares() {
        runner.run(context -> {
            List<SpanExporter> exporters = context.getBean(SpanExporters.class).list();
            assertEquals(2, exporters.size(), "no exporter may be dropped or left unwrapped");
            exporters.forEach(exporter -> assertInstanceOf(ExceptionSanitizingSpanExporter.class, exporter));
        });
    }

    @Test
    void winsOverSpringBootsUnwrappedCollection() {
        runner.withConfiguration(AutoConfigurations.of(
                        OpenTelemetrySdkAutoConfiguration.class,
                        OpenTelemetryTracingAutoConfiguration.class))
                .run(context -> context.getBean(SpanExporters.class)
                        .list()
                        .forEach(exporter -> assertInstanceOf(
                                ExceptionSanitizingSpanExporter.class,
                                exporter,
                                "the beforeName ordering no longer beats Boot's own SpanExporters bean")));
    }

    private static List<String> registeredAutoConfigurations() {
        List<String> names = new ArrayList<>();
        ImportCandidates.load(
                        AutoConfiguration.class,
                        SpanExportSanitizationAutoConfigurationTests.class.getClassLoader())
                .forEach(names::add);
        return names;
    }

    @Configuration(proxyBeanMethods = false)
    static class ExporterConfiguration {

        @Bean
        SpanExporter firstExporter() {
            return InMemorySpanExporter.create();
        }

        @Bean
        SpanExporter secondExporter() {
            return InMemorySpanExporter.create();
        }
    }
}
