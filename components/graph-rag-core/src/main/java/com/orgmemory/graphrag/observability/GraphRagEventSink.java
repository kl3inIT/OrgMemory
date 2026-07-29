package com.orgmemory.graphrag.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

    /**
     * Fans one event out to every sink so an application can observe the same
     * stage through more than one backend.
     *
     * <p>Sinks fail independently: one failing backend still lets the others
     * receive the event. The first failure is rethrown with the remaining ones
     * suppressed, so a caller that already treats emission as non-critical keeps
     * that behavior and a caller that does not still learns something broke.
     */
    static GraphRagEventSink composite(List<GraphRagEventSink> sinks) {
        List<GraphRagEventSink> delegates =
                List.copyOf(Objects.requireNonNull(sinks, "sinks"));
        if (delegates.isEmpty()) {
            return NO_OP;
        }
        if (delegates.size() == 1) {
            return delegates.getFirst();
        }
        return event -> {
            RuntimeException failure = null;
            for (GraphRagEventSink delegate : delegates) {
                try {
                    delegate.emit(event);
                } catch (RuntimeException sinkFailure) {
                    if (failure == null) {
                        failure = sinkFailure;
                    } else if (failure != sinkFailure) {
                        // Throwable.addSuppressed rejects self-suppression, and two
                        // sinks can raise one shared instance.
                        failure.addSuppressed(sinkFailure);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        };
    }

    record GraphRagEvent(
            UUID operationId,
            UUID organizationId,
            Stage stage,
            Outcome outcome,
            Duration duration,
            int inputCount,
            int outputCount,
            String modelRouteFingerprint,
            String scopeFingerprint,
            CacheStatus cacheStatus,
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
            scopeFingerprint = normalizeOptional(scopeFingerprint);
            failureCode = normalizeOptional(failureCode);
            if (modelRouteFingerprint != null
                    && !SHA_256.matcher(modelRouteFingerprint).matches()) {
                throw new IllegalArgumentException(
                        "modelRouteFingerprint must be a lowercase SHA-256 value");
            }
            if (scopeFingerprint != null
                    && !SHA_256.matcher(scopeFingerprint).matches()) {
                throw new IllegalArgumentException(
                        "scopeFingerprint must be a lowercase SHA-256 value");
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
        AUTHORIZE,
        PREPARE_QUERY,
        RETRIEVE_SNAPSHOT,
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

    enum CacheStatus {
        HIT,
        MISS,
        BYPASS
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
