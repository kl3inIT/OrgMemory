package com.orgmemory.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assistant.AssistantAgentActivity;
import com.orgmemory.core.assistant.AssistantAgentModelPort;
import com.orgmemory.core.assistant.AssistantCitation;
import com.orgmemory.core.assistant.AssistantService;
import com.orgmemory.core.assistant.AssistantTurn;
import com.orgmemory.core.assistant.observability.AssistantStageEventSink;
import com.orgmemory.core.assistant.observability.AssistantTurnEvent;
import com.orgmemory.core.knowledge.search.PermissionAwareKnowledgeSearch;
import com.orgmemory.core.knowledge.search.RetrievedKnowledgeEvidence;
import com.orgmemory.core.knowledge.search.SecureKnowledgeSearchResult;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.UserRole;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class AssistantAgentServiceTests {

    @Test
    void usesServerCreatedRouteAuthorityAndRelaysToolActivity() {
        PermissionAwareKnowledgeSearch retrieval = mock(PermissionAwareKnowledgeSearch.class);
        ChatModelPort chat = mock(ChatModelPort.class);
        AssistantAgentModelPort agent = mock(AssistantAgentModelPort.class);
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Laura",
                "laura@example.test",
                UserRole.MANAGER);
        String conversationId = UUID.randomUUID().toString();
        RetrievedKnowledgeEvidence evidence = evidence();
        AssistantModelRouteAuthority authority = new DefaultAssistantModelRouteAuthority(
                actor.organizationId(),
                new AiRoute("openai-main", "gpt-default"),
                null,
                0);
        when(retrieval.search(actor, "Handle this incident", 5, "request-agent"))
                .thenReturn(new SecureKnowledgeSearchResult(
                        "request-agent", List.of(evidence)));
        when(agent.stream(
                        eq(authority),
                        any(ChatGenerationRequest.class),
                        eq(conversationId),
                        eq(actor),
                        eq("request-agent"),
                        any()))
                .thenAnswer(invocation -> {
                    Consumer<AssistantAgentActivity> activities = invocation.getArgument(5);
                    activities.accept(new AssistantAgentActivity(
                            AssistantAgentActivity.Phase.SKILL_ACTIVATION,
                            AssistantAgentActivity.State.ACTIVE,
                            null));
                    activities.accept(new AssistantAgentActivity(
                            AssistantAgentActivity.Phase.SKILL_ACTIVATION,
                            AssistantAgentActivity.State.COMPLETE,
                            1));
                    return Flux.just("Incident workflow applied. [1]");
                });
        AssistantService service = new AssistantService(
                retrieval,
                chat,
                agent,
                io.micrometer.observation.ObservationRegistry.NOOP,
                AssistantTurnEvent.RetrievalEngine.GRAPH_RAG,
                AssistantStageEventSink.NO_OP);

        AssistantTurn turn = service.startTurn(
                actor,
                "Handle this incident",
                5,
                "request-agent",
                conversationId,
                authority,
                System.nanoTime());

        assertEquals(List.of("Incident workflow applied. [1]"),
                turn.content().collectList().block());
        assertEquals(List.of(
                        new AssistantAgentActivity(
                                AssistantAgentActivity.Phase.SKILL_ACTIVATION,
                                AssistantAgentActivity.State.ACTIVE,
                                null),
                        new AssistantAgentActivity(
                                AssistantAgentActivity.Phase.SKILL_ACTIVATION,
                                AssistantAgentActivity.State.COMPLETE,
                                1)),
                turn.activities().collectList().block());
        assertEquals(List.of(evidence), turn.citations().stream()
                .map(AssistantCitation::evidence)
                .toList());
        verify(agent).stream(
                eq(authority),
                any(ChatGenerationRequest.class),
                eq(conversationId),
                eq(actor),
                eq("request-agent"),
                any());
    }

    private static RetrievedKnowledgeEvidence evidence() {
        return new RetrievedKnowledgeEvidence(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Incident handbook",
                "Follow the incident response policy.",
                "https://example.test/incident",
                1,
                1,
                "Response",
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
