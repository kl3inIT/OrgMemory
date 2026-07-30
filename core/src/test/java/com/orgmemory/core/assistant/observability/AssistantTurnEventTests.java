package com.orgmemory.core.assistant.observability;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.assistant.observability.AssistantTurnEvent.Outcome;
import com.orgmemory.core.assistant.observability.AssistantTurnEvent.RetrievalEngine;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Generation is where prompts and completions exist, so a second telemetry surface for it is
 * the one most likely to erode the payload boundary — not by anyone deciding to weaken it, but
 * by a field nobody remembered to constrain. These assert the constraint is in the record
 * rather than in the discipline of whoever adds the next one.
 */
class AssistantTurnEventTests {

    private static final UUID ORGANIZATION = UUID.randomUUID();

    @Test
    void refusesAnExceptionMessageWhereAFailureCodeBelongs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> unavailable("Connection reset by peer while streaming 'What is our "
                        + "severance policy for the Berlin office?'"),
                "the only string component must be a bounded machine code, or the boundary is a"
                        + " convention");
    }

    @Test
    void acceptsABoundedMachineCode() {
        assertDoesNotThrow(() -> unavailable("assistant_stream_failed"));
    }

    @Test
    void refusesAnUnavailableTurnThatDoesNotSayWhy() {
        assertThrows(IllegalArgumentException.class, () -> unavailable(null));
    }

    /**
     * A turn cannot reach its first token after it ended. Catching that here rather than on a
     * chart means a mis-ordered measurement fails a build instead of quietly producing a
     * percentile nobody can explain.
     */
    @Test
    void refusesAFirstTokenLaterThanTheTurnItIsMeasuredWithin() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AssistantTurnEvent(
                        ORGANIZATION,
                        RetrievalEngine.GRAPH_RAG,
                        Outcome.ANSWERED,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(2),
                        3,
                        3,
                        null));
    }

    @Test
    void refusesNegativeDurationsAndCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> answered(Duration.ofMillis(-1), Duration.ofSeconds(1), 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> answered(Duration.ofMillis(10), Duration.ofSeconds(1), -1, 1));
    }

    /**
     * The difference between an answer that started slowly and one that never started. A zero
     * would read as instantaneous on the very chart it would ruin.
     */
    @Test
    void distinguishesATurnThatNeverEmittedFromOneThatEmittedImmediately() {
        assertFalse(answered(null, Duration.ofSeconds(1), 0, 0).started());
        assertTrue(answered(Duration.ZERO, Duration.ofSeconds(1), 0, 0).started());
    }

    @Test
    void treatsNoAccessibleEvidenceAsAnOutcomeRatherThanAFailure() {
        AssistantTurnEvent event = new AssistantTurnEvent(
                ORGANIZATION,
                RetrievalEngine.CANONICAL_HYBRID,
                Outcome.NO_EVIDENCE,
                null,
                Duration.ofMillis(120),
                0,
                0,
                null);

        assertFalse(
                event.outcome() == Outcome.UNAVAILABLE,
                "a correctly scoped tenant with nothing it may read is the permission model"
                        + " working, and counting it as an error makes that tenant look broken");
    }

    private static AssistantTurnEvent unavailable(String failureCode) {
        return new AssistantTurnEvent(
                ORGANIZATION,
                RetrievalEngine.GRAPH_RAG,
                Outcome.UNAVAILABLE,
                null,
                Duration.ofMillis(80),
                0,
                0,
                failureCode);
    }

    private static AssistantTurnEvent answered(
            Duration timeToFirstToken, Duration total, int evidence, int citations) {
        return new AssistantTurnEvent(
                ORGANIZATION,
                RetrievalEngine.GRAPH_RAG,
                Outcome.ANSWERED,
                timeToFirstToken,
                total,
                evidence,
                citations,
                null);
    }
}
