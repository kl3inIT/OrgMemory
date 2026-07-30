package com.orgmemory.integrations.observability;

import java.util.List;
import org.springframework.core.env.Environment;

/**
 * Refuses to start an application configured to let Spring AI copy prompt, completion or
 * tool-argument text into telemetry.
 *
 * <p>Spring AI gates content capture behind properties, and each property registers the
 * component that captures it. Read from the 2.0.0 jars rather than from the reference, the
 * shapes are two:
 *
 * <ul>
 *   <li>{@code spring.ai.chat.observations.log-prompt}, {@code log-completion}, the identically
 *       named pair under {@code spring.ai.chat.client.observations}, and
 *       {@code spring.ai.image.observations.log-prompt} each register a handler that writes the
 *       text to the application log at INFO. {@code include-error-logging} registers one that
 *       logs the throwable, which an OrgMemory exception can be carrying query, evidence or
 *       provider-response text inside.
 *   <li>{@code spring.ai.tools.observations.include-content} registers an observation filter
 *       that adds the call arguments to the span as {@code spring.ai.tool.call.arguments}.
 * </ul>
 *
 * <p>The property is the whole control, so the property is what this checks — against the
 * resolved {@link Environment} rather than against {@code application.yml}, because an
 * environment variable, a system property, a command-line argument and a profile all outrank
 * the file. That is the same reasoning as {@link ProviderLoggingBoundaryVerifier}, applied to
 * the other mechanism: there, the boundary is a logger level a deployment can lower; here, it
 * is a flag a deployment can raise.
 *
 * <p>Every flag is guarded, not only the ones a subsystem in use today would honour. A flag
 * whose autoconfiguration is absent resolves false and costs nothing to ask about, and the
 * alternative is a boundary that a dependency addition silently steps outside of.
 *
 * <p>Public because each application asserts against {@link #CONTENT_PROPERTIES} that its own
 * classpath declares no content flag this list has missed. A copy of the list in each test
 * would pass while the real one rotted.
 */
public final class ObservationContentBoundaryVerifier {

    /**
     * Every Spring AI property that turns telemetry content capture on. Ordered by family so a
     * new one lands beside its siblings.
     *
     * <p>The vector-store flag is on no application's classpath today; OrgMemory owns its own
     * pgvector access rather than using Spring AI's {@code VectorStore}. It is listed because
     * adding that starter would otherwise open a path nobody was asked about.
     */
    public static final List<String> CONTENT_PROPERTIES = List.of(
            "spring.ai.chat.observations.log-prompt",
            "spring.ai.chat.observations.log-completion",
            "spring.ai.chat.observations.include-error-logging",
            "spring.ai.chat.client.observations.log-prompt",
            "spring.ai.chat.client.observations.log-completion",
            "spring.ai.image.observations.log-prompt",
            "spring.ai.tools.observations.include-content",
            "spring.ai.vectorstore.observations.log-query-response");

    private ObservationContentBoundaryVerifier() { }

    /** @throws IllegalStateException if any content flag resolves true. */
    public static void verify(Environment environment) {
        List<String> open = CONTENT_PROPERTIES.stream()
                .filter(property -> environment.getProperty(property, Boolean.class, Boolean.FALSE))
                .toList();
        if (open.isEmpty()) {
            return;
        }
        throw new IllegalStateException(
                "Spring AI content capture is enabled by " + String.join(", ", open)
                        + ", which would put prompt, completion or tool-argument text into the"
                        + " application log or onto a span. Telemetry carries counts, never"
                        + " payload — see docs/decisions/0018-telemetry-carries-counts-never-payload.md."
                        + " Set every one of these to false. The resolved value is what matters, so"
                        + " check SPRING_AI_* environment variables, -Dspring.ai.* system"
                        + " properties, command-line arguments and profile-specific YAML, not only"
                        + " application.yml.");
    }
}
