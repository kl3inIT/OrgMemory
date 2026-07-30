package com.orgmemory.integrations.graphrag.observability;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Declaring a flag false in {@code application.yml} is a default, not a boundary: an
 * environment variable, a system property, a command-line argument or a profile all outrank the
 * file. So these set the flags the way a deployment's override would and check the resolved
 * value, not the configuration that produced it.
 */
class ObservationContentBoundaryVerifierTests {

    @Test
    void startsWhenNothingSetsAContentFlag() {
        assertDoesNotThrow(() -> ObservationContentBoundaryVerifier.verify(environmentWith(Map.of())));
    }

    @Test
    void startsWhenEveryContentFlagIsExplicitlyFalse() {
        Map<String, Object> properties = new HashMap<>();
        ObservationContentBoundaryVerifier.CONTENT_PROPERTIES
                .forEach(property -> properties.put(property, "false"));

        assertDoesNotThrow(() -> ObservationContentBoundaryVerifier.verify(environmentWith(properties)));
    }

    /**
     * One case per flag, so a failure names the family that is open rather than reporting that
     * "a flag" is. Sourced from the guarded list itself, which is why
     * {@link #guardsBothFamiliesThatShareTheirNames()} exists: dropping an entry would otherwise
     * remove the case that would have caught it.
     */
    @ParameterizedTest
    @MethodSource("contentProperties")
    void refusesToStartWhenAContentFlagIsTurnedOn(String property) {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ObservationContentBoundaryVerifier.verify(
                        environmentWith(Map.of(property, "true"))));

        assertTrue(failure.getMessage().contains(property), "the message must name what to fix");
    }

    @Test
    void reportsEveryOpenFlagRatherThanTheFirstOne() {
        Map<String, Object> properties = new HashMap<>();
        ObservationContentBoundaryVerifier.CONTENT_PROPERTIES
                .forEach(property -> properties.put(property, "true"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ObservationContentBoundaryVerifier.verify(environmentWith(properties)));

        ObservationContentBoundaryVerifier.CONTENT_PROPERTIES.forEach(property -> assertTrue(
                failure.getMessage().contains(property),
                () -> property + " is open but the failure does not mention it, so fixing the"
                        + " named flag would leave the application still refusing to start"));
    }

    /**
     * The realistic override is an environment variable, not a line in the file. Relaxed binding
     * is what makes {@code SPRING_AI_..._LOG_PROMPT} set the dotted property, and it is applied
     * by the property source rather than by the caller — so a verifier reading a plain map would
     * pass while the deployment logged prompts.
     */
    @Test
    void refusesToStartWhenTheFlagArrivesAsAnEnvironmentVariable() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("SPRING_AI_CHAT_CLIENT_OBSERVATIONS_LOG_PROMPT", "true")));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ObservationContentBoundaryVerifier.verify(environment));

        assertTrue(failure.getMessage().contains("spring.ai.chat.client.observations.log-prompt"));
    }

    /**
     * Spring AI names the ChatClient and ChatModel flags identically one layer apart, which is
     * how the second family went undeclared here until 2026-07-30. Losing either from the
     * guarded list reopens a path with no other test to notice.
     */
    @Test
    void guardsBothFamiliesThatShareTheirNames() {
        assertTrue(
                ObservationContentBoundaryVerifier.CONTENT_PROPERTIES.containsAll(List.of(
                        "spring.ai.chat.observations.log-prompt",
                        "spring.ai.chat.observations.log-completion",
                        "spring.ai.chat.observations.include-error-logging",
                        "spring.ai.chat.client.observations.log-prompt",
                        "spring.ai.chat.client.observations.log-completion")),
                "a family dropped from the list is a family nothing checks");
    }

    @Test
    void staysDiscoverableWithoutAnApplicationNamingIt() {
        List<String> names = new ArrayList<>();
        ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).forEach(names::add);

        assertTrue(
                names.contains(ObservationContentBoundaryAutoConfiguration.class.getName()),
                "META-INF/spring/…AutoConfiguration.imports no longer names this class, so an "
                        + "application could start with content capture enabled");
    }

    @Test
    void failsTheApplicationContextRatherThanLettingItServeTraffic() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(ObservationContentBoundaryAutoConfiguration.class))
                .withPropertyValues("spring.ai.tools.observations.include-content=true")
                .run(context -> assertTrue(
                        context.getStartupFailure() != null,
                        "startup must fail; an application that looks healthy while copying tool "
                                + "arguments onto spans is the outcome this guard exists to prevent"));
    }

    @Test
    void startsWhenTheApplicationLeavesTheFlagsAlone() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(ObservationContentBoundaryAutoConfiguration.class))
                .run(context -> assertTrue(
                        context.getStartupFailure() == null,
                        "the guard must not fail a context that never enabled anything, or the "
                                + "test above proves only that the bean throws"));
    }

    static List<String> contentProperties() {
        return ObservationContentBoundaryVerifier.CONTENT_PROPERTIES;
    }

    private static StandardEnvironment environmentWith(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }
}
