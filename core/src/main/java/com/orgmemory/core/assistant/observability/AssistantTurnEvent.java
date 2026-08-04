package com.orgmemory.core.assistant.observability;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * What one assistant turn is allowed to report about itself.
 *
 * <p>This record is the payload boundary for the assistant surface, in the same sense that
 * {@code GraphRagEvent} is the boundary for the GraphRAG one: no component can hold free text,
 * so an adapter can only publish counts, durations, bounded enumerations and an organization
 * identifier. Generation is the highest-risk stage in the system for payload leakage, because
 * it is where prompts and completions actually exist — which is exactly why the surface that
 * observes it is constrained by construction rather than by the discipline of whoever adds the
 * next field.
 *
 * <p>There is deliberately no request identifier and no conversation identifier. Correlation to
 * retrieval and to the model call is by trace context, which already spans them and costs
 * nothing here. A string field for an identifier is a string field, and the first person in a
 * hurry puts the question in it.
 *
 * <p>{@code timeToFirstToken} is null when no token was ever emitted, which is the difference
 * between an answer that started slowly and an answer that never started.
 */
public record AssistantTurnEvent(
        UUID organizationId,
        RetrievalEngine engine,
        Outcome outcome,
        Duration retrieval,
        Duration timeToFirstToken,
        Duration total,
        int evidenceCount,
        int citationCount,
        String failureCode) {

    /** Bounded machine codes only; the same shape the GraphRAG boundary accepts. */
    public static final Pattern FAILURE_CODE = Pattern.compile("[a-z0-9_]{1,64}");

    /** Which retrieval implementation produced the evidence this turn answered from. */
    public enum RetrievalEngine {
        GRAPH_RAG,
        CANONICAL_HYBRID
    }

    /**
     * {@code NO_EVIDENCE} is not a failure. The assistant answering "I could not find enough
     * accessible company knowledge" is the permission model working, and counting it as an
     * error would make a correctly scoped tenant look broken.
     */
    public enum Outcome {
        ANSWERED,
        NO_EVIDENCE,
        UNAVAILABLE
    }

    public AssistantTurnEvent {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(retrieval, "retrieval");
        Objects.requireNonNull(total, "total");
        if (retrieval.isNegative() || total.isNegative()) {
            throw new IllegalArgumentException("durations must not be negative");
        }
        if (retrieval.compareTo(total) > 0) {
            throw new IllegalArgumentException(
                    "retrieval must not exceed the turn it is measured within");
        }
        if (timeToFirstToken != null) {
            if (timeToFirstToken.isNegative()) {
                throw new IllegalArgumentException("timeToFirstToken must not be negative");
            }
            if (timeToFirstToken.compareTo(total) > 0) {
                throw new IllegalArgumentException(
                        "timeToFirstToken must not exceed the turn it is measured within");
            }
        }
        if (evidenceCount < 0 || citationCount < 0) {
            throw new IllegalArgumentException("counts must be non-negative");
        }
        failureCode = failureCode == null || failureCode.isBlank() ? null : failureCode.strip();
        if (outcome == Outcome.UNAVAILABLE && failureCode == null) {
            throw new IllegalArgumentException("failureCode is required for an unavailable turn");
        }
        if (failureCode != null && !FAILURE_CODE.matcher(failureCode).matches()) {
            throw new IllegalArgumentException("failureCode must be a bounded machine code");
        }
    }

    /** True when a token reached the caller, which is what makes the measurement meaningful. */
    public boolean started() {
        return timeToFirstToken != null;
    }
}
