package com.orgmemory.api.assistant;

import com.orgmemory.core.assistant.observability.AssistantStageEventSink;
import com.orgmemory.core.assistant.observability.AssistantStageEventSink.AssistantStageEvent;
import com.orgmemory.core.assistant.observability.AssistantTurnEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

/** Measures the history read without exposing its conversation or messages. */
final class ObservedChatMemory implements ChatMemory {

    private final ChatMemory delegate;
    private final AssistantStageEventSink events;
    private final AssistantTurnEvent.RetrievalEngine engine;

    ObservedChatMemory(
            ChatMemory delegate,
            AssistantStageEventSink events,
            AssistantTurnEvent.RetrievalEngine engine) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.events = Objects.requireNonNull(events, "events");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        delegate.add(conversationId, messages);
    }

    @Override
    public List<Message> get(String conversationId) {
        long startedAt = System.nanoTime();
        try {
            List<Message> messages = delegate.get(conversationId);
            emit(
                    AssistantStageEventSink.Outcome.SUCCEEDED,
                    startedAt,
                    null);
            return messages;
        } catch (RuntimeException | Error failure) {
            emit(
                    AssistantStageEventSink.Outcome.FAILED,
                    startedAt,
                    "history_load_failed");
            throw failure;
        }
    }

    @Override
    public void clear(String conversationId) {
        delegate.clear(conversationId);
    }

    private void emit(
            AssistantStageEventSink.Outcome outcome,
            long startedAt,
            String failureCode) {
        events.emit(new AssistantStageEvent(
                engine,
                AssistantStageEventSink.Stage.CONVERSATION_HISTORY_LOAD,
                outcome,
                Duration.ofNanos(Math.max(
                        0L,
                        System.nanoTime() - startedAt)),
                failureCode,
                Instant.now()));
    }
}
