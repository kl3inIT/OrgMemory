package com.orgmemory.graphrag.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Provider-neutral telemetry boundary. Events intentionally contain no query,
 * prompt, completion or evidence text.
 */
@FunctionalInterface
public interface GraphRagEventSink {

    Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    Pattern FAILURE_CODE = Pattern.compile("[a-z0-9_]{1,64}");

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
            if (modelRouteFingerprint != null
                    && !SHA_256.matcher(modelRouteFingerprint).matches()) {
                throw new IllegalArgumentException(
                        "modelRouteFingerprint must be a lowercase SHA-256 value");
            }
            if (outcome == Outcome.FAILED && failureCode == null) {
                throw new IllegalArgumentException(
                        "failureCode is required for a failed event");
            }
            if (failureCode != null
                    && !FAILURE_CODE.matcher(failureCode).matches()) {
                throw new IllegalArgumentException(
                        "failureCode must be a bounded machine code");
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
