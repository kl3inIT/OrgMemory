package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assistant.observability.AssistantStageEventSink;
import com.orgmemory.core.assistant.observability.AssistantTurnEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

class ObservedChatMemoryTests {

    @Test
    void measuresHistoryLoadingWithoutPublishingConversationOrMessages() {
        ChatMemory delegate = mock(ChatMemory.class);
        AssistantStageEventSink events =
                mock(AssistantStageEventSink.class);
        Message message = mock(Message.class);
        when(delegate.get("conversation-secret"))
                .thenReturn(List.of(message));
        var memory = new ObservedChatMemory(
                delegate,
                events,
                AssistantTurnEvent.RetrievalEngine.GRAPH_RAG);

        assertEquals(
                List.of(message),
                memory.get("conversation-secret"));

        ArgumentCaptor<AssistantStageEventSink.AssistantStageEvent> captured =
                ArgumentCaptor.forClass(
                        AssistantStageEventSink.AssistantStageEvent.class);
        verify(events).emit(captured.capture());
        assertEquals(
                AssistantStageEventSink.Stage.CONVERSATION_HISTORY_LOAD,
                captured.getValue().stage());
        assertEquals(
                AssistantStageEventSink.Outcome.SUCCEEDED,
                captured.getValue().outcome());
        assertEquals(null, captured.getValue().failureCode());
    }

    @Test
    void reportsAClosedFailureCodeAndPreservesTheMemoryFailure() {
        ChatMemory delegate = mock(ChatMemory.class);
        AssistantStageEventSink events =
                mock(AssistantStageEventSink.class);
        IllegalStateException failure =
                new IllegalStateException("private database detail");
        when(delegate.get("conversation-secret")).thenThrow(failure);
        var memory = new ObservedChatMemory(
                delegate,
                events,
                AssistantTurnEvent.RetrievalEngine.GRAPH_RAG);

        assertEquals(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () -> memory.get("conversation-secret")));

        ArgumentCaptor<AssistantStageEventSink.AssistantStageEvent> captured =
                ArgumentCaptor.forClass(
                        AssistantStageEventSink.AssistantStageEvent.class);
        verify(events).emit(captured.capture());
        assertEquals(
                AssistantStageEventSink.Outcome.FAILED,
                captured.getValue().outcome());
        assertEquals(
                "history_load_failed",
                captured.getValue().failureCode());
    }
}
