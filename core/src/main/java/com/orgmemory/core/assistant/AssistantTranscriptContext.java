package com.orgmemory.core.assistant;

import java.util.List;
import java.util.UUID;

/**
 * Reads prior conversation context for a model call. Read-only by contract:
 * the transcript is written by {@code beginTurn} and {@code completeTurn}, and
 * nothing on this path may add to it.
 *
 * <p>The unit is a completed turn, not a message. A turn only becomes context
 * once it holds both its question and its answer, which excludes the question
 * of the turn currently in flight without having to recognize it, and excludes
 * turns that failed before producing an answer. It also removes the need to
 * snap a message-counted window forward to a question boundary: a window
 * counted in whole turns can never begin on an answer.
 */
public interface AssistantTranscriptContext {

    /**
     * The most recent completed turns of one conversation, oldest first, each
     * turn's question immediately before its answer.
     *
     * <p>Scoped by organization as well as conversation so a model call can
     * never read across a tenant boundary. The actor needs no separate check:
     * a message belongs to its conversation through a composite foreign key,
     * and a conversation belongs to exactly one actor.
     */
    List<AssistantContextMessage> recentCompletedTurns(
            UUID organizationId, UUID conversationId, int maxTurns);
}
