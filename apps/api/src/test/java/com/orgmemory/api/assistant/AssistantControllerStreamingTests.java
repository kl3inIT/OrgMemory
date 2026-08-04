package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.assistant.AssistantAnswerFeedbackView;
import com.orgmemory.core.assistant.AssistantAnswerSentiment;
import com.orgmemory.core.assistant.AssistantCitation;
import com.orgmemory.core.assistant.AssistantConversationService;
import com.orgmemory.core.assistant.AssistantService;
import com.orgmemory.core.assistant.AssistantTurn;
import com.orgmemory.core.knowledge.search.RetrievedKnowledgeEvidence;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class AssistantControllerStreamingTests {

    @Test
    void publishesClosedServerOwnedStarters() {
        List<AssistantController.AssistantStarterPrompt> starters =
                controller().starters();

        assertEquals(
                List.of(
                        "What is the probation policy?",
                        "How do I submit a travel expense claim?",
                        "What is the product release process?"),
                starters.stream()
                        .map(AssistantController.AssistantStarterPrompt::prompt)
                        .toList());
    }

    @Test
    void delegatesFeedbackThroughTheAuthenticatedActor() {
        AssistantConversationService conversations =
                mock(AssistantConversationService.class);
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        Authentication authentication = mock(Authentication.class);
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Laura",
                "laura@example.test");
        UUID messageId = UUID.randomUUID();
        AssistantAnswerFeedbackView expected = new AssistantAnswerFeedbackView(
                messageId,
                AssistantAnswerSentiment.HELPFUL,
                Instant.parse("2026-08-04T10:00:00Z"));
        when(actors.current(authentication)).thenReturn(actor);
        when(conversations.setAnswerFeedback(
                        actor, messageId, AssistantAnswerSentiment.HELPFUL))
                .thenReturn(expected);
        AssistantController controller = new AssistantController(
                mock(AssistantService.class),
                conversations,
                mock(ChatMemory.class),
                actors,
                mock(AssistantProperties.class),
                mock(ObjectMapper.class));

        AssistantAnswerFeedbackView actual = controller.setFeedback(
                messageId,
                new AssistantController.AnswerFeedbackRequest(
                        AssistantAnswerSentiment.HELPFUL),
                authentication);
        controller.deleteFeedback(messageId, authentication);

        assertEquals(expected, actual);
        verify(conversations).deleteAnswerFeedback(actor, messageId);
    }

    @Test
    void usesOneServerOwnedIdentityForTheStreamAndPersistedAnswer() {
        AssistantService assistant = mock(AssistantService.class);
        AssistantConversationService conversations =
                mock(AssistantConversationService.class);
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        AssistantProperties properties = mock(AssistantProperties.class);
        Authentication authentication = mock(Authentication.class);
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Laura",
                "laura@example.test");
        UUID conversationId = UUID.randomUUID();
        when(actors.current(authentication)).thenReturn(actor);
        when(conversations.beginTurn(actor, null, "Question"))
                .thenReturn(conversationId);
        when(assistant.startTurn(
                        eq(actor),
                        eq("Question"),
                        eq(5),
                        anyString(),
                        eq(conversationId.toString())))
                .thenReturn(new AssistantTurn(
                        "request-1", List.of(), reactor.core.publisher.Flux.just("Answer")));
        when(properties.heartbeatInterval()).thenReturn(Duration.ofHours(1));
        when(properties.turnTimeout()).thenReturn(Duration.ofMinutes(1));
        AssistantController controller = new AssistantController(
                assistant,
                conversations,
                mock(ChatMemory.class),
                actors,
                properties,
                new ObjectMapper());

        List<String> frames = controller.chat(
                        new AssistantChatRequest("Question", 5, null), authentication)
                .getBody()
                .map(event -> event.data())
                .collectList()
                .block();

        ArgumentCaptor<UUID> messageId = ArgumentCaptor.forClass(UUID.class);
        verify(conversations).completeTurn(
                eq(actor), eq(conversationId), messageId.capture(), eq("Answer"));
        assertEquals(
                "{\"type\":\"start\",\"messageId\":\""
                        + messageId.getValue()
                        + "\"}",
                frames.getFirst());
    }

    @Test
    void streamsTheVerifiedRequestSnapshotWithoutWaitingForModelCompletion() {
        RetrievedKnowledgeEvidence evidence = evidence();
        Sinks.Many<String> model =
                Sinks.many().unicast().onBackpressureBuffer();
        AssistantTurn turn = new AssistantTurn(
                "request-1",
                List.of(new AssistantCitation(1, evidence)),
                model.asFlux());

        StepVerifier.create(controller().parts(turn))
                .assertNext(part -> assertInstanceOf(
                        AssistantStreamPart.StartStep.class,
                        part))
                .assertNext(part -> {
                    var source = assertInstanceOf(
                            AssistantStreamPart.SourceUrl.class,
                            part);
                    assertEquals(
                            "/api/citations/"
                                    + evidence.chunkId()
                                    + "/content",
                            source.url());
                    assertEquals(1, source.citationNumber());
                })
                .then(() -> model.tryEmitNext("answer"))
                .assertNext(part -> assertInstanceOf(
                        AssistantStreamPart.TextStart.class,
                        part))
                .assertNext(part -> {
                    var delta = assertInstanceOf(
                            AssistantStreamPart.TextDelta.class,
                            part);
                    assertEquals("answer", delta.delta());
                })
                .then(model::tryEmitComplete)
                .assertNext(part -> assertInstanceOf(
                        AssistantStreamPart.TextEnd.class,
                        part))
                .assertNext(part -> assertInstanceOf(
                        AssistantStreamPart.FinishStep.class,
                        part))
                .verifyComplete();
    }

    @Test
    void deletesTheOwnedTranscriptBeforeClearingBoundedMemory() {
        AssistantConversationService conversations =
                mock(AssistantConversationService.class);
        ChatMemory memory = mock(ChatMemory.class);
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        Authentication authentication = mock(Authentication.class);
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Laura",
                "laura@example.test");
        UUID conversationId = UUID.randomUUID();
        when(actors.current(authentication)).thenReturn(actor);
        AssistantController controller = new AssistantController(
                mock(AssistantService.class),
                conversations,
                memory,
                actors,
                mock(AssistantProperties.class),
                mock(ObjectMapper.class));

        controller.delete(conversationId, authentication);

        InOrder deletion = inOrder(conversations, memory);
        deletion.verify(conversations).delete(actor, conversationId);
        deletion.verify(memory).clear(conversationId.toString());
    }

    private static AssistantController controller() {
        return new AssistantController(
                mock(AssistantService.class),
                mock(AssistantConversationService.class),
                mock(ChatMemory.class),
                mock(CurrentActorProvider.class),
                mock(AssistantProperties.class),
                mock(ObjectMapper.class));
    }

    private static RetrievedKnowledgeEvidence evidence() {
        return new RetrievedKnowledgeEvidence(
                UUID.fromString(
                        "43000000-0000-0000-0000-000000000003"),
                UUID.fromString(
                        "43000000-0000-0000-0000-000000000004"),
                UUID.fromString(
                        "43000000-0000-0000-0000-000000000005"),
                UUID.fromString(
                        "43000000-0000-0000-0000-000000000006"),
                "Policy",
                "approved evidence",
                null,
                null,
                null,
                null,
                0.0,
                1.0,
                1.0,
                UUID.fromString(
                        "43000000-0000-0000-0000-000000000007"),
                UUID.fromString(
                        "43000000-0000-0000-0000-000000000007"),
                "model-v1",
                UUID.fromString(
                        "43000000-0000-0000-0000-000000000008"),
                1L);
    }
}
