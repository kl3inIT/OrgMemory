package com.orgmemory.core.assistant;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies one Assistant turn and the conversation it belongs to.
 *
 * <p>A turn spans two transactions — {@code beginTurn} persists the question and
 * {@code completeTurn} persists the answer once the stream finishes. Passing this
 * reference between them records the pairing the writers already know, so a
 * reader never has to infer it from sequence order. Concurrent turns in one
 * conversation can interleave their rows arbitrarily.
 */
public record AssistantTurnRef(UUID conversationId, UUID turnId) {

    public AssistantTurnRef {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(turnId, "turnId");
    }
}
