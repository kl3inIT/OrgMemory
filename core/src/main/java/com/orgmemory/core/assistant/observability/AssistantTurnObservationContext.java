package com.orgmemory.core.assistant.observability;

import io.micrometer.observation.Observation;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Carries one assistant turn's measurements from where they happen to where they are published.
 *
 * <p>A Micrometer context is mutable by design — the turn's outcome is not known when the
 * observation starts — so the mutability is confined to setters that accept only counts,
 * durations and enumerations. {@link #toEvent()} then reduces the whole thing to
 * {@link AssistantTurnEvent}, whose compact constructor is the boundary the convention reads
 * through. A convention that could reach past the event could put a completion on a span; one
 * that can only reach the event cannot.
 *
 * <p>The clock is nanosecond-based and captured at construction. Wall-clock time is not used:
 * a turn is measured in latency, and a clock adjustment mid-turn would otherwise produce a
 * negative time to first token that the event would then reject.
 */
public final class AssistantTurnObservationContext extends Observation.Context {

    private final UUID organizationId;
    private final AssistantTurnEvent.RetrievalEngine engine;
    private final long startedAtNanos;

    private Long firstTokenAtNanos;
    private Long completedAtNanos;
    private AssistantTurnEvent.Outcome outcome = AssistantTurnEvent.Outcome.UNAVAILABLE;
    private String failureCode = "not_recorded";
    private int evidenceCount;
    private int citationCount;

    public AssistantTurnObservationContext(
            UUID organizationId,
            AssistantTurnEvent.RetrievalEngine engine,
            long startedAtNanos) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.startedAtNanos = startedAtNanos;
    }

    /**
     * Records the moment the caller could first see something. Ignores every later token, so
     * the field means what its name says even though the stream calls this once per element.
     */
    public void firstTokenAt(long nanos) {
        if (firstTokenAtNanos == null) {
            firstTokenAtNanos = nanos;
        }
    }

    public void answered(long completedAtNanos, int evidenceCount, int citationCount) {
        this.outcome = AssistantTurnEvent.Outcome.ANSWERED;
        this.failureCode = null;
        this.completedAtNanos = completedAtNanos;
        this.evidenceCount = evidenceCount;
        this.citationCount = citationCount;
    }

    public void foundNoEvidence(long completedAtNanos) {
        this.outcome = AssistantTurnEvent.Outcome.NO_EVIDENCE;
        this.failureCode = null;
        this.completedAtNanos = completedAtNanos;
    }

    /**
     * @param failureCode a bounded machine code, never an exception message. The event rejects
     *     anything else, which is the point: the code that publishes cannot decide to be
     *     helpful with a stack trace.
     */
    public void unavailable(long completedAtNanos, String failureCode) {
        this.outcome = AssistantTurnEvent.Outcome.UNAVAILABLE;
        this.failureCode = failureCode;
        this.completedAtNanos = completedAtNanos;
    }

    public AssistantTurnEvent toEvent() {
        long end = completedAtNanos == null ? System.nanoTime() : completedAtNanos;
        return new AssistantTurnEvent(
                organizationId,
                engine,
                outcome,
                firstTokenAtNanos == null
                        ? null
                        : Duration.ofNanos(firstTokenAtNanos - startedAtNanos),
                Duration.ofNanos(Math.max(0L, end - startedAtNanos)),
                evidenceCount,
                citationCount,
                failureCode);
    }
}
