package com.orgmemory.worker.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

/**
 * The OpenAI and Anthropic client libraries build WARN messages that concatenate
 * the prompt and the response content. {@code spring.ai.chat.observations.log-prompt}
 * does not reach them because it only governs Spring AI observation handlers.
 *
 * <p>Every such call site is guarded by {@code isWarnEnabled()}, so the level
 * configured for those packages is what keeps the payload from being built at all.
 * The worker runs graph extraction over chunk content, so its prompts carry evidence
 * text directly.
 */
class ProviderLoggingBoundaryTests {

    /** Packages whose own logging concatenates OrgMemory prompt or completion text. */
    private static final List<String> PAYLOAD_LOGGING_PACKAGES = List.of(
            "org.springframework.ai.openai",
            "org.springframework.ai.anthropic");

    /** Levels at or above ERROR; anything else lets a guarded WARN site build its message. */
    private static final Set<String> LEVELS_ABOVE_WARN = Set.of("ERROR", "FATAL", "OFF");

    @Test
    void theBaseConfigurationPinsEveryPayloadLoggingPackageAboveWarn() throws IOException {
        Map<String, Object> properties = load(new ClassPathResource("application.yml"));

        for (String payloadPackage : PAYLOAD_LOGGING_PACKAGES) {
            // The loader returns origin-tracked char sequences rather than plain strings.
            Object level = properties.get("logging.level." + payloadPackage);
            assertEquals(
                    "ERROR",
                    String.valueOf(level),
                    () -> payloadPackage
                            + " must be pinned to ERROR in application.yml so its guarded WARN"
                            + " sites cannot concatenate the prompt");
        }
    }

    @Test
    void noProfileLowersAPayloadLoggingPackageBackToWarn() throws IOException {
        File[] profiles = profileConfigurationFiles();
        assertTrue(profiles.length > 0, "no application-<profile>.yml was scanned, so this test proves nothing");

        for (File profile : profiles) {
            Map<String, Object> properties = load(new FileSystemResource(profile));

            properties.forEach((key, value) -> {
                if (!key.startsWith("logging.level.")) {
                    return;
                }
                String logger = key.substring("logging.level.".length());
                if (PAYLOAD_LOGGING_PACKAGES.stream().noneMatch(p -> p.equals(logger) || p.startsWith(logger + "."))) {
                    return;
                }
                assertTrue(
                        LEVELS_ABOVE_WARN.contains(String.valueOf(value).toUpperCase(Locale.ROOT)),
                        () -> profile.getName() + " sets " + key + "=" + value
                                + ", which re-enables the provider WARN sites that log the prompt");
            });
        }
    }

    private static Map<String, Object> load(org.springframework.core.io.Resource resource) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(resource.getFilename(), resource);
        if (sources.size() != 1) {
            fail(resource.getFilename() + " must contain exactly one YAML document, found " + sources.size());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) sources.getFirst().getSource();
        return properties;
    }

    /**
     * Profile files live beside {@code application.yml} in this module's resources. Gradle
     * extracts them to a directory, so listing the parent is enough and keeps the test from
     * hard-coding a profile list that a new profile would silently escape.
     */
    private static File[] profileConfigurationFiles() throws IOException {
        File resources = new ClassPathResource("application.yml").getFile().getParentFile();
        File[] profiles = resources.listFiles(
                (directory, name) -> name.startsWith("application-") && name.endsWith(".yml"));
        return profiles == null ? new File[0] : profiles;
    }
}
