package com.orgmemory.graphrag.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Provider-neutral telemetry boundary. Events intentionally contain no query,
 * prompt, completion or evidence text.
 */
@FunctionalInterface
public interface GraphRagEventSink {

    GraphRagEventSink NO_OP = event -> { };

    void emit(GraphRagEvent event);

    record GraphRagEvent(
            UUID operationId,
            UUID organizationId,
            Stage stage,
            Outcome outcome,
            Duration duration,
            int inputCount,
            int outputCount,
            String modelRouteFingerprint,
            String failureCode,
            Instant occurredAt) {

        public GraphRagEvent {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(organizationId, "organizationId");
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(duration, "duration");
            if (duration.isNegative()) {
                throw new IllegalArgumentException("duration must not be negative");
            }
            if (inputCount < 0 || outputCount < 0) {
                throw new IllegalArgumentException("counts must be non-negative");
            }
            modelRouteFingerprint = normalizeOptional(modelRouteFingerprint);
            failureCode = normalizeOptional(failureCode);
            if (outcome == Outcome.FAILED && failureCode == null) {
                throw new IllegalArgumentException(
                        "failureCode is required for a failed event");
            }
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    enum Stage {
        PARSE,
        CHUNK,
        EXTRACT,
        GLEAN,
        MERGE,
        EMBED,
        PUBLISH,
        RETRIEVE,
        RERANK,
        ASSEMBLE_CONTEXT,
        GENERATE
    }

    enum Outcome {
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
