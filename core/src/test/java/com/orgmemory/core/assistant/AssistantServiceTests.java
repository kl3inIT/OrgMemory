package com.orgmemory.core.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.ChatGenerationRequest;
import com.orgmemory.core.ai.ChatModelPort;
import com.orgmemory.core.knowledge.RetrievedKnowledgeEvidence;
import com.orgmemory.core.knowledge.SecureKnowledgeRetrievalService;
import com.orgmemory.core.knowledge.SecureKnowledgeSearchResult;
import com.orgmemory.core.organization.CurrentActor;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

class AssistantServiceTests {

    private final SecureKnowledgeRetrievalService retrieval = mock(SecureKnowledgeRetrievalService.class);
    private final ChatModelPort chat = mock(ChatModelPort.class);
    private final CurrentActor actor = new CurrentActor(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Laura", "laura@example.test");
    private AssistantService service;

    @BeforeEach
    void setUp() {
        service = new AssistantService(retrieval, chat);
    }

    @Test
    void streamsOnlyPermissionVerifiedEvidenceToTheModel() {
        RetrievedKnowledgeEvidence evidence = evidence();
        when(retrieval.search(actor, "What is the probation policy?", 5, "request-1"))
                .thenReturn(new SecureKnowledgeSearchResult("request-1", List.of(evidence)));
        when(chat.stream(eq(AiWorkload.ASSISTANT_CHAT), any()))
                .thenReturn(Flux.just("The probation period ", "is 60 days. [1]"));

        AssistantTurn turn = service.startTurn(
                actor, "What is the probation policy?", 5, "request-1");

        assertEquals(List.of("The probation period ", "is 60 days. [1]"),
                turn.content().collectList().block());
        assertEquals(List.of(evidence), turn.evidence());
        ArgumentCaptor<ChatGenerationRequest> request = ArgumentCaptor.forClass(ChatGenerationRequest.class);
        verify(chat).stream(eq(AiWorkload.ASSISTANT_CHAT), request.capture());
        assertEquals(true, request.getValue().userPrompt().contains(evidence.content()));
        assertEquals(true, request.getValue().systemInstruction().contains("untrusted data"));
    }

    @Test
    void doesNotCallTheModelWhenNoAccessibleEvidenceExists() {
        when(retrieval.search(actor, "Show me the financial forecast", 5, "request-2"))
                .thenReturn(new SecureKnowledgeSearchResult("request-2", List.of()));

        AssistantTurn turn = service.startTurn(
                actor, "Show me the financial forecast", 5, "request-2");

        assertEquals(List.of(AssistantService.NO_ACCESSIBLE_EVIDENCE), turn.content().collectList().block());
        verify(chat, never()).stream(any(), any());
    }

    @Test
    void asynchronousProviderFailureIsReportedAsUnavailable() {
        when(retrieval.search(actor, "Question", 5, "request-3"))
                .thenReturn(new SecureKnowledgeSearchResult("request-3", List.of(evidence())));
        when(chat.stream(eq(AiWorkload.ASSISTANT_CHAT), any()))
                .thenReturn(Flux.error(new IllegalStateException("provider secret")));

        AssistantTurn turn = service.startTurn(actor, "Question", 5, "request-3");

        assertThrows(AssistantUnavailableException.class, () -> turn.content().blockLast());
    }

    private static RetrievedKnowledgeEvidence evidence() {
        return new RetrievedKnowledgeEvidence(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Employee Handbook",
                "The probation period is 60 days.",
                "https://example.test/employee-handbook",
                4,
                4,
                "Probation",
                0.8,
                0.9,
                0.95,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "model-1",
                UUID.randomUUID(),
                1L);
    }
}
