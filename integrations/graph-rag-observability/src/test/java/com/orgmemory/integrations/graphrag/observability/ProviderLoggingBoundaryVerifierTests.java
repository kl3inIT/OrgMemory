package com.orgmemory.integrations.graphrag.observability;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The YAML pin is a default, not a boundary: an environment variable, a system property, a
 * logback configuration or a level on a more specific logger all outrank it. This verifier
 * exists to catch that, so the tests set the level the way an operator's override would and
 * check the resolved state rather than the configuration that produced it.
 */
class ProviderLoggingBoundaryVerifierTests {

    private static final String LEAK_SITE = "org.springframework.ai.openai.OpenAiChatModel";

    private final List<String> touched = new ArrayList<>();

    /**
     * The test JVM has no logging configuration, so every leak site starts WARN-enabled. Each
     * test begins from the closed state the shipped YAML produces and then reopens exactly one,
     * so a failure names the site the test is about rather than the default.
     */
    @org.junit.jupiter.api.BeforeEach
    void closeEveryLeakSite() {
        ProviderLoggingBoundaryVerifier.PAYLOAD_LOGGING_CLASSES
                .forEach(name -> setLevel(name, Level.ERROR));
    }

    @AfterEach
    void restoreLevels() {
        touched.forEach(name -> loggerContext().getLogger(name).setLevel(null));
        touched.clear();
    }

    @Test
    void startsWhenEveryLeakSiteIsAboveWarn() {
        assertDoesNotThrow(() -> ProviderLoggingBoundaryVerifier.verify(classLoader()));
    }

    @Test
    void refusesToStartWhenAnOverrideReopensAProviderLeakSite() {
        setLevel(LEAK_SITE, Level.WARN);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ProviderLoggingBoundaryVerifier.verify(classLoader()));

        assertTrue(failure.getMessage().contains(LEAK_SITE), "the message must name what to fix");
        assertTrue(
                failure.getMessage().contains("LOGGING_LEVEL"),
                "the message must point at the override that outranked the YAML pin");
    }

    @Test
    void guardsEveryClassKnownToConcatenatePayloadIntoAWarnMessage() {
        assertTrue(
                ProviderLoggingBoundaryVerifier.PAYLOAD_LOGGING_CLASSES.containsAll(List.of(
                        "org.springframework.ai.openai.OpenAiChatModel",
                        "org.springframework.ai.anthropic.AnthropicChatModel")),
                "dropping a known leak site from the list silently reopens it");
    }

    @Test
    void refusesToStartWhenTheLevelIsLoweredBelowWarnRatherThanToIt() {
        setLevel(LEAK_SITE, Level.DEBUG);

        assertThrows(
                IllegalStateException.class,
                () -> ProviderLoggingBoundaryVerifier.verify(classLoader()));
    }

    @Test
    void checksTheLeakSiteRatherThanItsPackageSoAChildOverrideCannotHide() {
        setLevel("org.springframework.ai.openai", Level.ERROR);
        setLevel(LEAK_SITE, Level.WARN);

        assertThrows(
                IllegalStateException.class,
                () -> ProviderLoggingBoundaryVerifier.verify(classLoader()),
                "a package pinned to ERROR must not excuse a class set back to WARN");
    }

    @Test
    void staysDiscoverableWithoutAnApplicationNamingIt() {
        List<String> names = new ArrayList<>();
        ImportCandidates.load(AutoConfiguration.class, classLoader()).forEach(names::add);

        assertTrue(
                names.contains(ProviderLoggingBoundaryAutoConfiguration.class.getName()),
                "META-INF/spring/…AutoConfiguration.imports no longer names this class, so an "
                        + "application could start with prompt logging enabled");
    }

    @Test
    void failsTheApplicationContextRatherThanLettingItServeTraffic() {
        setLevel(LEAK_SITE, Level.WARN);

        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(ProviderLoggingBoundaryAutoConfiguration.class))
                .run(context -> assertTrue(
                        context.getStartupFailure() != null,
                        "startup must fail; an application that looks healthy while logging "
                                + "prompts is the outcome this guard exists to prevent"));
    }

    private void setLevel(String logger, Level level) {
        touched.add(logger);
        loggerContext().getLogger(logger).setLevel(level);
    }

    private static LoggerContext loggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    private static ClassLoader classLoader() {
        return ProviderLoggingBoundaryVerifierTests.class.getClassLoader();
    }
}
