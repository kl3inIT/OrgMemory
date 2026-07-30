package com.orgmemory.worker.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.orgmemory.integrations.graphrag.observability.ObservationContentBoundaryVerifier;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StreamUtils;

/**
 * Spring AI captures prompt, completion and tool-argument content only when a property turns it
 * on, and it names two of those families identically one layer apart —
 * {@code spring.ai.chat.observations} for the ChatModel and
 * {@code spring.ai.chat.client.observations} for the ChatClient above it.
 *
 * <p>These cover the shipped configuration. The configuration is not the enforcement — an
 * environment variable outranks it — which is what {@code ObservationContentBoundaryVerifier}
 * checks at startup against the resolved value. What only a test can do is ask this
 * application's own classpath whether Spring AI declares a content flag the verifier has never
 * heard of, which is how a dependency bump would otherwise open a path silently.
 */
class ObservationContentBoundaryTests {

    /** Flags this application exercises, and therefore writes out rather than leaving to default. */
    private static final List<String> DECLARED_FLAGS = List.of(
            "spring.ai.chat.observations.log-prompt",
            "spring.ai.chat.observations.log-completion",
            "spring.ai.chat.observations.include-error-logging",
            "spring.ai.chat.client.observations.log-prompt",
            "spring.ai.chat.client.observations.log-completion");

    /**
     * Properties under an {@code observations} prefix that do not govern content capture, and so
     * are deliberately outside the verifier's list. Empty today: every such property Spring AI
     * 2.0.0 declares on this classpath turns content capture on.
     */
    private static final Set<String> ACKNOWLEDGED_NON_CONTENT = Set.of();

    @Test
    void theBaseConfigurationDeclaresEveryExercisedContentFlagFalse() throws IOException {
        Map<String, Object> properties = load(new ClassPathResource("application.yml"));

        for (String flag : DECLARED_FLAGS) {
            // The loader returns origin-tracked values rather than plain booleans.
            assertEquals(
                    "false",
                    String.valueOf(properties.get(flag)).toLowerCase(Locale.ROOT),
                    () -> flag + " must be declared false in application.yml so the payload"
                            + " posture is readable in one place");
        }
    }

    @Test
    void noProfileTurnsAGuardedContentFlagBackOn() throws IOException {
        File[] profiles = profileConfigurationFiles();
        assertTrue(profiles.length > 0, "no application-<profile>.yml was scanned, so this test proves nothing");

        for (File profile : profiles) {
            Map<String, Object> properties = load(new FileSystemResource(profile));

            ObservationContentBoundaryVerifier.CONTENT_PROPERTIES.forEach(flag -> {
                Object value = properties.get(flag);
                if (value == null) {
                    return;
                }
                assertEquals(
                        "false",
                        String.valueOf(value).toLowerCase(Locale.ROOT),
                        () -> profile.getName() + " sets " + flag + "=" + value
                                + ", which captures prompt, completion or tool-argument content");
            });
        }
    }

    /**
     * Reads what Spring AI itself declares on this application's classpath rather than trusting
     * the reference documentation, which is how the ChatClient family was missed. A module added
     * by a dependency bump brings its own metadata, so a new content flag fails here before it
     * can be set anywhere.
     */
    @Test
    void theVerifierGuardsEveryContentFlagSpringAiDeclaresOnThisClasspath() throws IOException {
        Set<String> declared = observationPropertiesOnClasspath();
        assertTrue(
                declared.contains("spring.ai.chat.client.observations.log-prompt"),
                "no Spring AI configuration metadata was read, so this test proves nothing");

        Set<String> unguarded = new TreeSet<>(declared);
        unguarded.removeAll(ObservationContentBoundaryVerifier.CONTENT_PROPERTIES);
        unguarded.removeAll(ACKNOWLEDGED_NON_CONTENT);

        assertTrue(
                unguarded.isEmpty(),
                () -> "Spring AI declares " + unguarded + " on this classpath and"
                        + " ObservationContentBoundaryVerifier does not guard them. Add each to"
                        + " CONTENT_PROPERTIES, or — if it does not capture content — to this"
                        + " test's ACKNOWLEDGED_NON_CONTENT with the reason.");
    }

    /** Every {@code spring.ai.*.observations.*} property any jar on the classpath declares. */
    private static Set<String> observationPropertiesOnClasspath() throws IOException {
        Set<String> names = new TreeSet<>();
        Resource[] metadata = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:META-INF/spring-configuration-metadata.json");

        for (Resource resource : metadata) {
            String json;
            try (InputStream stream = resource.getInputStream()) {
                json = StreamUtils.copyToString(stream, StandardCharsets.UTF_8);
            }
            if (!(JsonParserFactory.getJsonParser().parseMap(json).get("properties")
                    instanceof List<?> properties)) {
                continue;
            }
            for (Object property : properties) {
                Object name = ((Map<?, ?>) property).get("name");
                if (name instanceof String candidate
                        && candidate.startsWith("spring.ai.")
                        && candidate.contains(".observations.")) {
                    names.add(candidate);
                }
            }
        }
        return names;
    }

    private static Map<String, Object> load(Resource resource) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(resource.getFilename(), resource);
        if (sources.size() != 1) {
            fail(resource.getFilename() + " must contain exactly one YAML document, found " + sources.size());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) sources.getFirst().getSource();
        return properties;
    }

    private static File[] profileConfigurationFiles() throws IOException {
        File resources = new ClassPathResource("application.yml").getFile().getParentFile();
        File[] profiles = resources.listFiles((directory, name) -> name.startsWith("application-")
                && (name.endsWith(".yml") || name.endsWith(".yaml")));
        return profiles == null ? new File[0] : profiles;
    }
}
