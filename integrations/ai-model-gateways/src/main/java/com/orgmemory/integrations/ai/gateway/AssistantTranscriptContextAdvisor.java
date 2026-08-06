package com.orgmemory.integrations.ai.gateway;

import com.orgmemory.core.assistant.AssistantContextMessage;
import com.orgmemory.core.assistant.AssistantTranscriptContext;
import com.orgmemory.core.assistant.observability.AssistantStageEventSink;
import com.orgmemory.core.assistant.observability.AssistantStageEventSink.AssistantStageEvent;
import com.orgmemory.core.assistant.observability.AssistantTurnEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Places prior turns of the same conversation in front of the current prompt.
 *
 * <p>Replaces Spring AI's {@code MessageChatMemoryAdvisor}. That advisor keeps a
 * second copy of the conversation in its own store and writes to it from the
 * response aggregation callback, while OrgMemory persists the answer itself once
 * the stream completes. Two writers of the same conversation cannot both be
 * canonical, so this one only reads.
 *
 * <p>It reads whole completed turns rather than a message count. The question of
 * the turn in flight is already persisted by {@code beginTurn} before the model
 * is called, and the caller passes that same question as the user message, so a
 * message-counted read would send it twice; an incomplete turn is excluded here
 * by construction instead.
 */
final class AssistantTranscriptContextAdvisor implements BaseAdvisor {

    /**
     * Deliberately not {@code ChatMemory.CONVERSATION_ID}: nothing on this path
     * depends on Spring AI's chat-memory contract any more.
     */
    static final String CONVERSATION_ID = "orgmemory_assistant_conversation_id";

    private final AssistantTranscriptContext transcript;
    private final AssistantStageEventSink events;
    private final AssistantTurnEvent.RetrievalEngine engine;
    private final UUID organizationId;
    private final int maxTurns;

    AssistantTranscriptContextAdvisor(
            AssistantTranscriptContext transcript,
            AssistantStageEventSink events,
            AssistantTurnEvent.RetrievalEngine engine,
            UUID organizationId,
            int maxTurns) {
        this.transcript = Objects.requireNonNull(transcript, "transcript");
        this.events = Objects.requireNonNull(events, "events");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        if (maxTurns <= 0) {
            throw new IllegalArgumentException("maxTurns must be positive");
        }
        this.maxTurns = maxTurns;
    }

    @Override
    public int getOrder() {
        // The same slot MessageChatMemoryAdvisor occupied, which keeps the tool
        // loop running inside this advisor rather than around it.
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        UUID conversationId = conversationId(request);
        if (conversationId == null) {
            return request;
        }
        List<AssistantContextMessage> priorTurns = measured(conversationId);
        if (priorTurns.isEmpty()) {
            return request;
        }
        List<Message> messages = new ArrayList<>();
        for (AssistantContextMessage message : priorTurns) {
            messages.add(switch (message.role()) {
                case USER -> new UserMessage(message.content());
                case ASSISTANT -> new AssistantMessage(message.content());
            });
        }
        messages.addAll(request.prompt().getInstructions());
        hoistSystemMessage(messages);
        return request.mutate()
                .prompt(request.prompt().mutate().messages(messages).build())
                .build();
    }

    /**
     * Keeps the {@code CONVERSATION_HISTORY_LOAD} stage the replaced
     * {@code ObservedChatMemory} decorator published. The event carries a
     * duration and an outcome only; neither the conversation nor its messages
     * reach telemetry.
     */
    private List<AssistantContextMessage> measured(UUID conversationId) {
        long startedAt = System.nanoTime();
        try {
            List<AssistantContextMessage> priorTurns =
                    transcript.recentCompletedTurns(organizationId, conversationId, maxTurns);
            emit(AssistantStageEventSink.Outcome.SUCCEEDED, startedAt, null);
            return priorTurns;
        } catch (RuntimeException | Error failure) {
            emit(AssistantStageEventSink.Outcome.FAILED, startedAt, "history_load_failed");
            throw failure;
        }
    }

    private void emit(
            AssistantStageEventSink.Outcome outcome, long startedAt, String failureCode) {
        events.emit(new AssistantStageEvent(
                engine,
                AssistantStageEventSink.Stage.CONVERSATION_HISTORY_LOAD,
                outcome,
                Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAt)),
                failureCode,
                Instant.now()));
    }

    /** Read-only: the answer is persisted by the turn writer, not from here. */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    /**
     * The current system message carries this turn's permission-verified
     * grounding, and it must stay first once history is prepended.
     */
    private static void hoistSystemMessage(List<Message> messages) {
        for (int index = 0; index < messages.size(); index++) {
            if (messages.get(index) instanceof SystemMessage system) {
                messages.remove(index);
                messages.add(0, system);
                return;
            }
        }
    }

    private static UUID conversationId(ChatClientRequest request) {
        Object value = request.context().get(CONVERSATION_ID);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException notAnIdentity) {
            return null;
        }
    }
}
