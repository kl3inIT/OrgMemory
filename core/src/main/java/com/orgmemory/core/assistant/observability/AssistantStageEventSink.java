package com.orgmemory.core.assistant.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Payload-free boundary for assistant latency stages above retrieval.
 */
@FunctionalInterface
public interface AssistantStageEventSink {

    AssistantStageEventSink NO_OP = event -> {
    };

    enum Stage {
        GROUNDING_TO_PROMPT,
        CONVERSATION_HISTORY_LOAD,
        RETRIEVAL_TO_FIRST_TOKEN
    }

    enum Outcome {
        SUCCEEDED,
        FAILED
    }

    record AssistantStageEvent(
            AssistantTurnEvent.RetrievalEngine engine,
            Stage stage,
            Outcome outcome,
            Duration duration,
            String failureCode,
            Instant occurredAt) {

        public AssistantStageEvent {
            Objects.requireNonNull(engine, "engine");
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(occurredAt, "occurredAt");
            if (duration.isNegative()) {
                throw new IllegalArgumentException(
                        "duration must not be negative");
            }
            failureCode = failureCode == null || failureCode.isBlank()
                    ? null
                    : failureCode.strip();
            if (outcome == Outcome.FAILED && failureCode == null) {
                throw new IllegalArgumentException(
                        "failureCode is required for a failed stage");
            }
            if (failureCode != null
                    && !AssistantTurnEvent.FAILURE_CODE
                            .matcher(failureCode)
                            .matches()) {
                throw new IllegalArgumentException(
                        "failureCode must be a bounded machine code");
            }
        }
    }

    void emit(AssistantStageEvent event);

    static AssistantStageEventSink composite(
            List<AssistantStageEventSink> sinks) {
        List<AssistantStageEventSink> delegates = List.copyOf(
                Objects.requireNonNull(sinks, "sinks"));
        if (delegates.isEmpty()) {
            return NO_OP;
        }
        return event -> delegates.forEach(sink -> sink.emit(event));
    }

    static AssistantStageEventSink failureTolerant(
            AssistantStageEventSink delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return event -> {
            try {
                delegate.emit(event);
            } catch (RuntimeException ignored) {
                // Telemetry cannot make an otherwise valid assistant turn fail.
            }
        };
    }
}
