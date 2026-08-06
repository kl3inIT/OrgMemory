package com.orgmemory.integrations.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assistant.AssistantContextMessage;
import com.orgmemory.core.assistant.AssistantConversationRole;
import com.orgmemory.core.assistant.AssistantTranscriptContext;
import com.orgmemory.core.assistant.observability.AssistantStageEventSink;
import com.orgmemory.core.assistant.observability.AssistantTurnEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

class AssistantTranscriptContextAdvisorTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    private final AssistantTranscriptContext transcript = mock(AssistantTranscriptContext.class);
    private final AssistantStageEventSink events = mock(AssistantStageEventSink.class);

    @Test
    void placesPriorTurnsBeforeTheCurrentQuestionAndKeepsGroundingFirst() {
        when(transcript.recentCompletedTurns(ORGANIZATION_ID, CONVERSATION_ID, 10))
                .thenReturn(List.of(
                        new AssistantContextMessage(
                                AssistantConversationRole.USER, "Earlier question"),
                        new AssistantContextMessage(
                                AssistantConversationRole.ASSISTANT, "Earlier answer")));

        List<Message> advised = advise(request(
                new SystemMessage("Verified grounding for this turn"),
                new UserMessage("Current question")));

        // The system message carries this turn's permission-verified grounding
        // and must stay first once history is prepended.
        assertInstanceOf(SystemMessage.class, advised.getFirst());
        assertEquals(
                List.of(
                        "Verified grounding for this turn",
                        "Earlier question",
                        "Earlier answer",
                        "Current question"),
                advised.stream().map(Message::getText).toList());
        assertInstanceOf(UserMessage.class, advised.get(1));
        assertInstanceOf(AssistantMessage.class, advised.get(2));
    }

    @Test
    void neverWritesBackToTheTranscript() {
        ChatClientResponse response = mock(ChatClientResponse.class);

        // The turn writer persists the answer once the stream completes. An
        // advisor that also wrote would either duplicate it or race it.
        assertSame(response, advisor().after(response, mock(AdvisorChain.class)));
        verify(transcript, never()).recentCompletedTurns(any(), any(), anyInt());
    }

    @Test
    void leavesThePromptUntouchedWhenThereIsNoConversationOrNoHistory() {
        ChatClientRequest withoutConversation = ChatClientRequest.builder()
                .prompt(new Prompt(List.of(new UserMessage("Current question"))))
                .build();
        assertSame(
                withoutConversation,
                advisor().before(withoutConversation, mock(AdvisorChain.class)));
        verify(transcript, never()).recentCompletedTurns(any(), any(), anyInt());

        when(transcript.recentCompletedTurns(ORGANIZATION_ID, CONVERSATION_ID, 10))
                .thenReturn(List.of());
        ChatClientRequest firstTurn = request(new UserMessage("Current question"));
        assertSame(firstTurn, advisor().before(firstTurn, mock(AdvisorChain.class)));
    }

    @Test
    void ignoresAConversationIdentifierThatIsNotOne() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(List.of(new UserMessage("Current question"))))
                .context(AssistantTranscriptContextAdvisor.CONVERSATION_ID, "not-a-uuid")
                .build();

        assertSame(request, advisor().before(request, mock(AdvisorChain.class)));
        verify(transcript, never()).recentCompletedTurns(any(), any(), anyInt());
    }

    @Test
    void publishesTheHistoryLoadStageWithoutTheConversationOrItsMessages() {
        when(transcript.recentCompletedTurns(ORGANIZATION_ID, CONVERSATION_ID, 10))
                .thenReturn(List.of(new AssistantContextMessage(
                        AssistantConversationRole.USER, "Earlier question")));

        advise(request(new UserMessage("Current question")));

        ArgumentCaptor<AssistantStageEventSink.AssistantStageEvent> event =
                ArgumentCaptor.forClass(AssistantStageEventSink.AssistantStageEvent.class);
        verify(events).emit(event.capture());
        assertEquals(
                AssistantStageEventSink.Stage.CONVERSATION_HISTORY_LOAD,
                event.getValue().stage());
        assertEquals(
                AssistantStageEventSink.Outcome.SUCCEEDED,
                event.getValue().outcome());
    }

    @Test
    void reportsAFailedHistoryLoadAsABoundedCodeAndStillFails() {
        when(transcript.recentCompletedTurns(ORGANIZATION_ID, CONVERSATION_ID, 10))
                .thenThrow(new IllegalStateException("connection reset for user laura@example.test"));

        try {
            advisor().before(request(new UserMessage("Current question")), mock(AdvisorChain.class));
        } catch (IllegalStateException expected) {
            // The failure must reach the caller rather than degrade to a
            // context-free turn that silently forgets the conversation.
        }

        ArgumentCaptor<AssistantStageEventSink.AssistantStageEvent> event =
                ArgumentCaptor.forClass(AssistantStageEventSink.AssistantStageEvent.class);
        verify(events).emit(event.capture());
        assertEquals(
                AssistantStageEventSink.Outcome.FAILED, event.getValue().outcome());
        assertEquals("history_load_failed", event.getValue().failureCode());
    }

    @Test
    void occupiesTheSlotTheReplacedMemoryAdvisorHeld() {
        // Below ToolCallingAdvisor, so the tool loop runs inside this advisor
        // and tool messages never become conversation context.
        assertEquals(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER, advisor().getOrder());
    }

    private List<Message> advise(ChatClientRequest request) {
        return advisor()
                .before(request, mock(AdvisorChain.class))
                .prompt()
                .getInstructions();
    }

    private AssistantTranscriptContextAdvisor advisor() {
        return new AssistantTranscriptContextAdvisor(
                transcript,
                events,
                AssistantTurnEvent.RetrievalEngine.GRAPH_RAG,
                ORGANIZATION_ID,
                10);
    }

    private static ChatClientRequest request(Message... messages) {
        return ChatClientRequest.builder()
                .prompt(new Prompt(List.of(messages)))
                .context(
                        AssistantTranscriptContextAdvisor.CONVERSATION_ID,
                        CONVERSATION_ID.toString())
                .build();
    }
}
