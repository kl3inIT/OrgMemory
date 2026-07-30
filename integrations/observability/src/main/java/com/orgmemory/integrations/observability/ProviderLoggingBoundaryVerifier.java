package com.orgmemory.integrations.observability;

import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;

/**
 * Refuses to start an application whose configuration would let a provider library log a
 * prompt.
 *
 * <p>The OpenAI and Anthropic clients build WARN messages by concatenating the prompt or the
 * response content, guarded by {@code isWarnEnabled()}. Pinning those packages in
 * {@code application.yml} makes the guard closed by default, but it is only a default:
 * {@code LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AI_OPENAI}, a system property, a
 * {@code logback.xml}, or a level set on a subordinate logger all outrank it. Configuration
 * cannot enforce a boundary that configuration can undo.
 *
 * <p>So the check is made against the resolved state rather than against any source of it:
 * each class that holds a payload-logging call site is asked whether WARN is enabled on its
 * own logger, which is the exact question its guard will ask at runtime, with inheritance
 * already applied. Startup fails rather than proceeds, because the alternative is an
 * application that looks healthy while writing customer text to disk.
 */
final class ProviderLoggingBoundaryVerifier {

    /**
     * Classes whose own logging concatenates prompt or response content into a WARN message.
     * These are logger names, not merely packages: a level set on a child logger overrides an
     * ancestor, so only the leak site's own logger answers the question its guard asks.
     */
    static final List<String> PAYLOAD_LOGGING_CLASSES = List.of(
            "org.springframework.ai.openai.OpenAiChatModel",
            "org.springframework.ai.openai.OpenAiAudioSpeechModel",
            "org.springframework.ai.anthropic.AnthropicChatModel");

    private ProviderLoggingBoundaryVerifier() { }

    static void verify(ClassLoader classLoader) {
        List<String> open = PAYLOAD_LOGGING_CLASSES.stream()
                .filter(name -> ClassUtils.isPresent(name, classLoader))
                .filter(name -> LoggerFactory.getLogger(name).isWarnEnabled())
                .toList();
        if (open.isEmpty()) {
            return;
        }
        throw new IllegalStateException(
                "WARN logging is enabled for " + String.join(", ", open)
                        + ", which would write prompt and response content to the application log."
                        + " Those classes build the message only when WARN is enabled, so the level"
                        + " is the boundary. Something outranked the ERROR pin in application.yml:"
                        + " check LOGGING_LEVEL_* environment variables, -Dlogging.level.*, a"
                        + " logback configuration, and any level set on a more specific logger."
                        + " spring.ai.chat.observations.log-prompt does not govern these paths.");
    }
}
