package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.ai.AssistantModelAuthorityService;
import com.orgmemory.core.ai.AssistantModelChoice;
import com.orgmemory.core.ai.AssistantModelSelectionRef;
import com.orgmemory.core.assistant.AssistantAnswerFeedbackView;
import com.orgmemory.core.assistant.AssistantAnswerSentiment;
import com.orgmemory.core.assistant.AssistantCitation;
import com.orgmemory.core.assistant.AssistantCitationReference;
import com.orgmemory.core.assistant.AssistantConversationService;
import com.orgmemory.core.assistant.AssistantEvidenceService;
import com.orgmemory.core.assistant.AssistantEvidenceUploadService;
import com.orgmemory.core.assistant.AssistantService;
import com.orgmemory.core.assistant.AssistantTurn;
import com.orgmemory.core.assistant.AssistantTurnRef;
import com.orgmemory.core.knowledge.search.RetrievedKnowledgeEvidence;
import com.orgmemory.core.knowledge.retrieval.CitationEvidenceService;
import com.orgmemory.core.knowledge.retrieval.CitationEvidenceReference;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Mono;
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
                actors,
                mock(AssistantProperties.class),
                mock(AssistantModelAuthorityService.class),
                mock(CitationEvidenceService.class),
                mock(AssistantRetrievalScheduler.class),
                mock(AssistantEvidenceUploadService.class),
                mock(AssistantEvidenceService.class),
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
    void exposesSafeModelChoicesAndPersistsOnlyAnAuthorizedSelectionReference() {
        AssistantConversationService conversations =
                mock(AssistantConversationService.class);
        AssistantModelAuthorityService authority =
                mock(AssistantModelAuthorityService.class);
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        Authentication authentication = mock(Authentication.class);
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Laura",
                "laura@example.test");
        UUID conversationId = UUID.randomUUID();
        UUID activationId = UUID.randomUUID();
        AssistantModelSelectionRef stored = new AssistantModelSelectionRef(
                activationId, UUID.randomUUID(), 9);
        when(actors.current(authentication)).thenReturn(actor);
        when(conversations.modelSelection(actor, conversationId)).thenReturn(stored);
        when(authority.resolveSelectedActivation(actor.organizationId(), stored))
                .thenReturn(activationId);
        when(authority.choices(actor.organizationId())).thenReturn(List.of(
                new AssistantModelChoice(
                        null,
                        "Organization AI",
                        "openai",
                        "gpt-default",
                        "Organization default",
                        true),
                new AssistantModelChoice(
                        activationId,
                        "Organization AI",
                        "openai",
                        "gpt-fast",
                        "Fast",
                        false)));
        when(authority.selectionRef(null)).thenReturn(stored);
        AssistantController controller = new AssistantController(
                mock(AssistantService.class),
                conversations,
                actors,
                mock(AssistantProperties.class),
                authority,
                mock(CitationEvidenceService.class),
                mock(AssistantRetrievalScheduler.class),
                mock(AssistantEvidenceUploadService.class),
                mock(AssistantEvidenceService.class),
                mock(ObjectMapper.class));

        AssistantController.AssistantModelOptionsResponse response =
                controller.modelOptions(conversationId, authentication);
        controller.selectModel(
                conversationId,
                new AssistantController.SelectAssistantModelRequest(activationId),
                authentication);

        assertEquals(activationId, response.selectedModelActivationId());
        assertEquals(List.of("gpt-default", "gpt-fast"), response.options().stream()
                .map(AssistantController.AssistantModelOptionResponse::modelId)
                .toList());
        verify(authority).authorize(actor.organizationId(), activationId);
        verify(conversations).selectModel(actor, conversationId, stored);
    }

    @Test
    void hydratesOnlyCurrentlyVisibleCitationsWithoutCachingTheAuthorizationResult() {
        AssistantConversationService conversations = mock(AssistantConversationService.class);
        CitationEvidenceService evidenceService = mock(CitationEvidenceService.class);
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        Authentication authentication = mock(Authentication.class);
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Laura",
                "laura@example.test");
        UUID messageId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        when(actors.current(authentication)).thenReturn(actor);
        when(conversations.citationReferences(actor, messageId))
                .thenReturn(List.of(new AssistantCitationReference(1, chunkId)));
        when(evidenceService.hydrate(eq(actor), eq(List.of(chunkId)), anyString()))
                .thenReturn(List.of(new CitationEvidenceReference(
                        chunkId, "Employee Handbook", "Probation", 2, 2)));
        AssistantController controller = new AssistantController(
                mock(AssistantService.class),
                conversations,
                actors,
                mock(AssistantProperties.class),
                mock(AssistantModelAuthorityService.class),
                evidenceService,
                mock(AssistantRetrievalScheduler.class),
                mock(AssistantEvidenceUploadService.class),
                mock(AssistantEvidenceService.class),
                mock(ObjectMapper.class));

        var response = controller.citations(messageId, authentication);

        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("Employee Handbook", response.getBody().getFirst().title());
        assertEquals(
                "/api/citations/" + chunkId + "/excerpt",
                response.getBody().getFirst().excerptUrl());
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
        AssistantTurnRef turnRef = new AssistantTurnRef(conversationId, UUID.randomUUID());
        when(conversations.beginTurn(actor, null, "Question", null))
                .thenReturn(turnRef);
        when(assistant.startTurn(
                        eq(actor),
                        eq("Question"),
                        eq(5),
                        anyString(),
                        eq(conversationId.toString()),
                        isNull(),
                        anyLong()))
                .thenReturn(new AssistantTurn(
                        "request-1", List.of(), reactor.core.publisher.Flux.just("Answer")));
        when(properties.heartbeatInterval()).thenReturn(Duration.ofHours(1));
        when(properties.turnTimeout()).thenReturn(Duration.ofMinutes(1));
        AssistantRetrievalScheduler scheduler = immediateScheduler();
        AssistantController controller = new AssistantController(
                assistant,
                conversations,
                actors,
                properties,
                mock(AssistantModelAuthorityService.class),
                mock(CitationEvidenceService.class),
                scheduler,
                mock(AssistantEvidenceUploadService.class),
                mock(AssistantEvidenceService.class),
                new ObjectMapper());

        List<String> frames = controller.chat(
                        new AssistantChatRequest("Question", 5, null, null), authentication)
                .getBody()
                .map(event -> event.data())
                .collectList()
                .block();

        ArgumentCaptor<UUID> messageId = ArgumentCaptor.forClass(UUID.class);
        verify(conversations).completeTurn(
                eq(actor),
                eq(turnRef),
                messageId.capture(),
                eq("Answer"),
                eq(List.of()));
        assertEquals(
                "{\"type\":\"start\",\"messageId\":\""
                        + messageId.getValue()
                        + "\"}",
                frames.getFirst());
    }

    @Test
    void emitsStreamStartAndRetrievalActivityWhileRetrievalIsStillBlocked()
            throws InterruptedException {
        AssistantService assistant = mock(AssistantService.class);
        AssistantConversationService conversations = mock(AssistantConversationService.class);
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
        CountDownLatch retrievalEntered = new CountDownLatch(1);
        CountDownLatch releaseRetrieval = new CountDownLatch(1);
        AtomicBoolean retrievalCompleted = new AtomicBoolean();
        when(actors.current(authentication)).thenReturn(actor);
        AssistantTurnRef turnRef = new AssistantTurnRef(conversationId, UUID.randomUUID());
        when(conversations.beginTurn(actor, null, "Question", null))
                .thenReturn(turnRef);
        when(assistant.startTurn(
                        eq(actor),
                        eq("Question"),
                        eq(5),
                        anyString(),
                        eq(conversationId.toString()),
                        isNull(),
                        anyLong()))
                .thenAnswer(invocation -> {
                    retrievalEntered.countDown();
                    releaseRetrieval.await();
                    retrievalCompleted.set(true);
                    return new AssistantTurn(
                            "request-1", List.of(), reactor.core.publisher.Flux.just("Answer"));
                });
        when(properties.heartbeatInterval()).thenReturn(Duration.ofHours(1));
        when(properties.turnTimeout()).thenReturn(Duration.ofMinutes(1));
        AssistantRetrievalScheduler scheduler =
                new AssistantRetrievalScheduler(1, 1, Duration.ofSeconds(1));
        AssistantController controller = new AssistantController(
                assistant,
                conversations,
                actors,
                properties,
                mock(AssistantModelAuthorityService.class),
                mock(CitationEvidenceService.class),
                scheduler,
                mock(AssistantEvidenceUploadService.class),
                mock(AssistantEvidenceService.class),
                new ObjectMapper());

        try {
            StepVerifier.create(controller.chat(
                                    new AssistantChatRequest("Question", 5, null, null),
                                    authentication)
                            .getBody())
                    .assertNext(event -> assertTrue(event.data().contains("\"type\":\"start\"")))
                    .assertNext(event -> assertEquals(
                            "{\"type\":\"start-step\"}", event.data()))
                    .assertNext(event -> {
                        assertTrue(event.data().contains("data-assistantActivity"));
                        assertTrue(event.data().contains("\"phase\":\"RETRIEVAL\""));
                        assertTrue(event.data().contains("\"state\":\"ACTIVE\""));
                    })
                    .then(() -> {
                        assertTrue(await(retrievalEntered));
                        assertTrue(!retrievalCompleted.get());
                        releaseRetrieval.countDown();
                    })
                    .thenConsumeWhile(ignored -> true)
                    .verifyComplete();
        } finally {
            releaseRetrieval.countDown();
            scheduler.close();
        }
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
                .assertNext(part -> {
                    var activity = assertInstanceOf(
                            AssistantStreamPart.Activity.class,
                            part);
                    assertEquals(AssistantStreamPart.Activity.Phase.RETRIEVAL, activity.phase());
                    assertEquals(AssistantStreamPart.Activity.State.COMPLETE, activity.state());
                    assertEquals(1, activity.evidenceCount());
                })
                .assertNext(part -> {
                    var activity = assertInstanceOf(
                            AssistantStreamPart.Activity.class,
                            part);
                    assertEquals(AssistantStreamPart.Activity.Phase.GENERATION, activity.phase());
                    assertEquals(AssistantStreamPart.Activity.State.ACTIVE, activity.state());
                })
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
    void deletesAConversationThroughTheOwnedTranscriptAlone() {
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
        UUID conversationId = UUID.randomUUID();
        when(actors.current(authentication)).thenReturn(actor);
        AssistantController controller = new AssistantController(
                mock(AssistantService.class),
                conversations,
                actors,
                mock(AssistantProperties.class),
                mock(AssistantModelAuthorityService.class),
                mock(CitationEvidenceService.class),
                mock(AssistantRetrievalScheduler.class),
                mock(AssistantEvidenceUploadService.class),
                mock(AssistantEvidenceService.class),
                mock(ObjectMapper.class));

        controller.delete(conversationId, authentication);

        // One store, one owned call. The second delete this replaced ran outside
        // the transaction against a table with no tenant column.
        verify(conversations).delete(actor, conversationId);
        verifyNoMoreInteractions(conversations);
    }

    private static AssistantController controller() {
        return new AssistantController(
                mock(AssistantService.class),
                mock(AssistantConversationService.class),
                mock(CurrentActorProvider.class),
                mock(AssistantProperties.class),
                mock(AssistantModelAuthorityService.class),
                mock(CitationEvidenceService.class),
                mock(AssistantRetrievalScheduler.class),
                mock(AssistantEvidenceUploadService.class),
                mock(AssistantEvidenceService.class),
                mock(ObjectMapper.class));
    }

    @SuppressWarnings("unchecked")
    private static AssistantRetrievalScheduler immediateScheduler() {
        AssistantRetrievalScheduler scheduler = mock(AssistantRetrievalScheduler.class);
        when(scheduler.schedule(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> Mono.fromCallable(invocation.getArgument(0)));
        return scheduler;
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
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
