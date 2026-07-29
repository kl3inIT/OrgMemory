package com.orgmemory.worker.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * The worker has no bootable context test, so its export defaults are asserted against the
 * shipped configuration instead. The API proves the same defaults behaviourally, by starting
 * and finding no OTLP meter registry.
 *
 * <p>Micrometer's OTLP metrics registry is opt-out with a {@code localhost:4318} URL, which
 * inside a container is that container. Production ran that way from deployment until
 * 2026-07-29, exporting to nowhere and logging the failure every minute in both services.
 */
class TelemetryExportDefaultsTests {

    @Test
    void metricsExportStaysOffUntilADeploymentNamesACollector() throws IOException {
        assertEquals(
                "false",
                withNothingSetInTheEnvironment("management.otlp.metrics.export.enabled"),
                "an opt-out exporter with a loopback default URL must not be left at its default");
    }

    @Test
    void logExportStaysOffBecauseTheCollectorTailsTheContainerLogInstead() throws IOException {
        assertEquals(
                "false",
                withNothingSetInTheEnvironment("management.logging.export.otlp.enabled"),
                "OTLP log export would duplicate every line and fail the same silent way");
    }

    @Test
    void batchWorkIsSampledInFullBecauseATenthOfItIsMostlyNothing() throws IOException {
        assertEquals(
                "1.0",
                withNothingSetInTheEnvironment("management.tracing.sampling.probability"),
                "the worker runs a low-volume batch workload; the API is the one that needs "
                        + "sampling");
    }

    @Test
    void telemetryCarriesEnoughIdentityForACollectorToTellDeploymentsApart() throws IOException {
        Map<String, Object> configuration = configuration();

        for (String attribute : List.of("service.version", "deployment.environment.name")) {
            assertEquals(
                    true,
                    configuration.containsKey(
                            "management.opentelemetry.resource-attributes." + attribute),
                    () -> attribute + " is missing, so a collector cannot separate builds "
                            + "or environments");
        }
    }

    /**
     * Resolves a configured value the way a deployment that sets no variables would see it, so
     * the assertion is about the default that ships rather than about the placeholder text.
     */
    private static String withNothingSetInTheEnvironment(String key) throws IOException {
        Object configured = configuration().get(key);
        assertNotNull(configured, () -> key + " is not configured at all");
        return new PropertyPlaceholderHelper("${", "}", ":", null, true)
                .replacePlaceholders(String.valueOf(configured), placeholder -> null);
    }

    private static Map<String, Object> configuration() throws IOException {
        Resource resource = new ClassPathResource("application.yml");
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load(resource.getFilename(), resource);
        if (sources.size() != 1) {
            fail("application.yml must contain exactly one YAML document, found " + sources.size());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) sources.getFirst().getSource();
        return properties;
    }
}
