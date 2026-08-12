package com.orgmemory.core.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.AssistantModelSelectionRef;
import com.orgmemory.core.knowledge.search.KnowledgeEvidenceSelection;
import com.orgmemory.core.knowledge.search.RetrievedKnowledgeEvidence;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.error.BusinessValidationException;
import com.orgmemory.core.shared.error.BusinessNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssistantConversationServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");

    private final AssistantConversationRepository conversations =
            mock(AssistantConversationRepository.class);
    private final AssistantConversationMessageRepository messages =
            mock(AssistantConversationMessageRepository.class);
    private final AssistantMessageCitationRepository citations =
            mock(AssistantMessageCitationRepository.class);
    private final AssistantAnswerFeedbackRepository answerFeedback =
            mock(AssistantAnswerFeedbackRepository.class);
    private final AssistantEvidenceService evidence =
            mock(AssistantEvidenceService.class);
    private final CurrentActor actor = new CurrentActor(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Laura",
            "laura@example.test");
    private AssistantConversationService service;

    @BeforeEach
    void setUp() {
        service = new AssistantConversationService(
                conversations,
                messages,
                citations,
                answerFeedback,
                evidence,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAnActorOwnedConversationAndStoresTheRawUserTurn() {
        AssistantTurnRef turn = service.beginTurn(
                actor,
                null,
                "  How do I submit an expense claim?  ");
        UUID conversationId = turn.conversationId();

        ArgumentCaptor<AssistantConversation> conversation =
                ArgumentCaptor.forClass(AssistantConversation.class);
        verify(conversations).save(conversation.capture());
        assertEquals(conversationId, conversation.getValue().getId());
        assertEquals(actor.organizationId(), conversation.getValue().organizationId());
        assertEquals(actor.userId(), conversation.getValue().actorUserId());
        assertEquals(
                "How do I submit an expense claim?",
                conversation.getValue().title());

        ArgumentCaptor<AssistantConversationMessage> message =
                ArgumentCaptor.forClass(AssistantConversationMessage.class);
        verify(messages).save(message.capture());
        assertEquals(AssistantConversationRole.USER, message.getValue().view().role());
        assertEquals(
                "How do I submit an expense claim?",
                message.getValue().view().content());
        assertEquals(turn.turnId(), message.getValue().turnId());
    }

    @Test
    void snapshotsOwnedPrivateFilesOnTheUserTurn() {
        AssistantFileTurnService privateFiles = mock(AssistantFileTurnService.class);
        AssistantConversationService privateService = new AssistantConversationService(
                conversations,
                messages,
                citations,
                answerFeedback,
                evidence,
                privateFiles,
                Clock.fixed(NOW, ZoneOffset.UTC));
        List<UUID> requested = List.of(UUID.randomUUID(), UUID.randomUUID());
        AssistantPrivateFileSelection selection = new AssistantPrivateFileSelection(List.of(
                new AssistantPrivateFileSelection.Item(requested.get(0), 1),
                new AssistantPrivateFileSelection.Item(requested.get(1), 1)));
        when(privateFiles.claim(eq(actor), any(), any(), any(), eq(requested)))
                .thenReturn(selection);

        AssistantPrivateFileTurnClaim claim = privateService.beginTurnWithPrivateFiles(
                actor, null, "Compare these files", null, requested);

        assertEquals(selection, claim.selection());
        verify(messages).saveAndFlush(any(AssistantConversationMessage.class));
        verify(privateFiles).claim(
                eq(actor), eq(claim.turn().conversationId()), any(), any(), eq(requested));
    }

    @Test
    void writesBothHalvesOfOneTurnUnderTheIdentityBeginTurnAllocated() {
        AssistantTurnRef turn = service.beginTurn(actor, null, "How long is probation?");
        when(conversations.findForUpdateByIdAndOrganizationIdAndActorUserId(
                        turn.conversationId(),
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(ownedConversation(turn.conversationId())));

        service.completeTurn(actor, turn, UUID.randomUUID(), "Sixty days.");

        ArgumentCaptor<AssistantConversationMessage> saved =
                ArgumentCaptor.forClass(AssistantConversationMessage.class);
        verify(messages, times(2)).save(saved.capture());
        assertEquals(
                List.of(AssistantConversationRole.USER, AssistantConversationRole.ASSISTANT),
                saved.getAllValues().stream().map(m -> m.view().role()).toList());
        // The pairing is recorded by the writers, not inferred later from
        // sequence order, which concurrent turns interleave.
        assertEquals(
                List.of(turn.turnId(), turn.turnId()),
                saved.getAllValues().stream().map(AssistantConversationMessage::turnId).toList());
    }

    @Test
    void givesConcurrentTurnsInOneConversationDistinctIdentities() {
        AssistantConversation conversation = ownedConversation(UUID.randomUUID());
        when(conversations.findForUpdateByIdAndOrganizationIdAndActorUserId(
                        conversation.getId(),
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(conversation));

        AssistantTurnRef first = service.beginTurn(actor, conversation.getId(), "First");
        AssistantTurnRef second = service.beginTurn(actor, conversation.getId(), "Second");

        assertEquals(first.conversationId(), second.conversationId());
        assertNotEquals(first.turnId(), second.turnId());
    }

    @Test
    void rejectsAnotherActorsConversationWithoutWritingAUserTurn() {
        UUID conversationId = UUID.randomUUID();
        when(conversations.findForUpdateByIdAndOrganizationIdAndActorUserId(
                        conversationId,
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.empty());

        assertThrows(
                AssistantConversationNotFoundException.class,
                () -> service.beginTurn(actor, conversationId, "Continue"));

        verify(messages, never()).save(any());
    }

    @Test
    void rejectsInvalidUserInputBeforeWritingConversationState() {
        assertThrows(
                BusinessValidationException.class,
                () -> service.beginTurn(actor, null, "  "));

        verify(conversations, never()).save(any());
        verify(messages, never()).save(any());
    }

    @Test
    void acceptsEightThousandCharactersInAnOrdinaryTurn() {
        String messageText = "x".repeat(8_000);

        service.beginTurn(actor, null, messageText);

        ArgumentCaptor<AssistantConversationMessage> saved =
                ArgumentCaptor.forClass(AssistantConversationMessage.class);
        verify(messages).save(saved.capture());
        assertEquals(messageText, saved.getValue().view().content());
    }

    @Test
    void acceptsEightThousandCharactersInAnEvidenceBoundTurn() {
        String messageText = "x".repeat(8_000);
        UUID bindingId = UUID.randomUUID();
        var selection = KnowledgeEvidenceSelection.restricted(List.of(
                new KnowledgeEvidenceSelection.Item(
                        bindingId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID())));
        when(evidence.claimForTurn(
                        eq(actor),
                        any(),
                        any(),
                        any(),
                        eq(List.of(bindingId))))
                .thenReturn(selection);

        AssistantEvidenceTurnClaim claim = service.beginTurnWithEvidence(
                actor,
                null,
                messageText,
                null,
                List.of(bindingId));

        assertEquals(selection, claim.selection());
        verify(messages).saveAndFlush(any());
    }

    @Test
    void rejectsMoreThanEightThousandCharactersBeforeAnyTurnStateIsWritten() {
        assertThrows(
                BusinessValidationException.class,
                () -> service.beginTurnWithEvidence(
                        actor,
                        null,
                        "x".repeat(8_001),
                        null,
                        List.of(UUID.randomUUID())));

        verify(conversations, never()).save(any());
        verify(messages, never()).saveAndFlush(any());
        verifyNoInteractions(evidence);
    }

    @Test
    void storesAndReplacesOnlyServerAuthorizedConversationModelReferences() {
        UUID conversationId = UUID.randomUUID();
        AssistantConversation conversation = ownedConversation(conversationId);
        AssistantModelSelectionRef first = new AssistantModelSelectionRef(
                UUID.randomUUID(), UUID.randomUUID(), 4);
        when(conversations.findForUpdateByIdAndOrganizationIdAndActorUserId(
                        conversationId,
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(conversation));
        when(conversations.findByIdAndOrganizationIdAndActorUserId(
                        conversationId,
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(conversation));

        service.beginTurn(actor, conversationId, "Use the selected model", first);
        assertEquals(first, service.modelSelection(actor, conversationId));

        service.selectModel(actor, conversationId, null);
        assertEquals(null, service.modelSelection(actor, conversationId));
    }

    @Test
    void locksTheConversationWhileCompletingATurn() {
        AssistantTurnRef turn = service.beginTurn(actor, null, "How long is probation?");
        when(conversations.findForUpdateByIdAndOrganizationIdAndActorUserId(
                        turn.conversationId(),
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(ownedConversation(turn.conversationId())));

        service.completeTurn(actor, turn, UUID.randomUUID(), "Sixty days.");

        // Completion touches the conversation. Reading it without the lock lets
        // a concurrent turn win the version race and roll this answer back after
        // the caller already streamed it.
        verify(conversations, never()).findByIdAndOrganizationIdAndActorUserId(
                eq(turn.conversationId()),
                eq(actor.organizationId()),
                eq(actor.userId()));
    }

    @Test
    void persistsTheServerAllocatedAssistantMessageIdentity() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(conversations.findForUpdateByIdAndOrganizationIdAndActorUserId(
                        conversationId,
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(ownedConversation(conversationId)));

        service.completeTurn(
                actor,
                new AssistantTurnRef(conversationId, UUID.randomUUID()),
                messageId,
                "The probation period is 60 days. [1]");

        ArgumentCaptor<AssistantConversationMessage> saved =
                ArgumentCaptor.forClass(AssistantConversationMessage.class);
        verify(messages).save(saved.capture());
        assertEquals(messageId, saved.getValue().getId());
        assertEquals(
                AssistantConversationRole.ASSISTANT,
                saved.getValue().view().role());
    }

    @Test
    void persistsServerDeclaredCitationReferencesWithTheCompletedAnswer() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        when(conversations.findForUpdateByIdAndOrganizationIdAndActorUserId(
                        conversationId,
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(ownedConversation(conversationId)));
        RetrievedKnowledgeEvidence evidence = mock(RetrievedKnowledgeEvidence.class);
        when(evidence.chunkId()).thenReturn(chunkId);
        when(evidence.title()).thenReturn("Policy");
        when(evidence.content()).thenReturn("approved evidence");

        service.completeTurn(
                actor,
                new AssistantTurnRef(conversationId, UUID.randomUUID()),
                messageId,
                "The probation period is 60 days. [1]",
                List.of(new AssistantCitation(1, evidence)));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Iterable> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(citations).saveAll(saved.capture());
        AssistantMessageCitation citation =
                (AssistantMessageCitation) saved.getValue().iterator().next();
        assertEquals(new AssistantCitationReference(1, chunkId), citation.view());
    }

    @Test
    void readsCitationReferencesOnlyThroughNonLockingActorOwnedAssistantLookup() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        AssistantConversationMessage answer = new AssistantConversationMessage(
                messageId,
                conversationId,
                UUID.randomUUID(),
                actor.organizationId(),
                actor.userId(),
                AssistantConversationRole.ASSISTANT,
                "Sixty days. [1]",
                NOW);
        when(messages.findOneByIdAndOrganizationIdAndActorUserIdAndRole(
                        messageId,
                        actor.organizationId(),
                        actor.userId(),
                        AssistantConversationRole.ASSISTANT))
                .thenReturn(Optional.of(answer));
        AssistantMessageCitation citation = new AssistantMessageCitation(
                UUID.randomUUID(),
                messageId,
                actor.organizationId(),
                actor.userId(),
                1,
                chunkId);
        when(citations.findAllByMessageIdOrderByCitationNumber(messageId))
                .thenReturn(List.of(citation));

        assertEquals(
                List.of(new AssistantCitationReference(1, chunkId)),
                service.citationReferences(actor, messageId));
        verify(messages, never()).findByIdAndOrganizationIdAndActorUserIdAndRole(
                eq(messageId),
                eq(actor.organizationId()),
                eq(actor.userId()),
                eq(AssistantConversationRole.ASSISTANT));
    }

    @Test
    void disclosesNoStoredCitationIdsForAnotherActorsMessage() {
        UUID messageId = UUID.randomUUID();
        when(messages.findOneByIdAndOrganizationIdAndActorUserIdAndRole(
                        messageId,
                        actor.organizationId(),
                        actor.userId(),
                        AssistantConversationRole.ASSISTANT))
                .thenReturn(Optional.empty());

        BusinessNotFoundException failure = assertThrows(
                BusinessNotFoundException.class,
                () -> service.citationReferences(actor, messageId));

        assertEquals("assistant.answer-not-found", failure.code());
        verify(citations, never()).findAllByMessageIdOrderByCitationNumber(eq(messageId));
    }

    @Test
    void createsReplacesAndRemovesFeedbackForAnOwnedAssistantAnswer() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        AssistantConversationMessage answer = new AssistantConversationMessage(
                messageId,
                conversationId,
                UUID.randomUUID(),
                actor.organizationId(),
                actor.userId(),
                AssistantConversationRole.ASSISTANT,
                "Sixty days. [1]",
                NOW);
        when(messages.findByIdAndOrganizationIdAndActorUserIdAndRole(
                        messageId,
                        actor.organizationId(),
                        actor.userId(),
                        AssistantConversationRole.ASSISTANT))
                .thenReturn(Optional.of(answer));
        when(answerFeedback.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AssistantAnswerFeedbackView created = service.setAnswerFeedback(
                actor, messageId, AssistantAnswerSentiment.HELPFUL);

        assertEquals(messageId, created.messageId());
        assertEquals(AssistantAnswerSentiment.HELPFUL, created.sentiment());

        AssistantAnswerFeedback existing = new AssistantAnswerFeedback(
                messageId,
                actor.organizationId(),
                actor.userId(),
                AssistantAnswerSentiment.HELPFUL,
                NOW.minusSeconds(30));
        when(answerFeedback.findById(messageId)).thenReturn(Optional.of(existing));

        AssistantAnswerFeedbackView replaced = service.setAnswerFeedback(
                actor, messageId, AssistantAnswerSentiment.NOT_HELPFUL);
        assertEquals(AssistantAnswerSentiment.NOT_HELPFUL, replaced.sentiment());

        service.deleteAnswerFeedback(actor, messageId);
        verify(answerFeedback).delete(existing);
    }

    @Test
    void usesOneOpaqueNotFoundSurfaceForAnUnownedOrNonAssistantTarget() {
        UUID messageId = UUID.randomUUID();
        when(messages.findByIdAndOrganizationIdAndActorUserIdAndRole(
                        messageId,
                        actor.organizationId(),
                        actor.userId(),
                        AssistantConversationRole.ASSISTANT))
                .thenReturn(Optional.empty());

        BusinessNotFoundException failure = assertThrows(
                BusinessNotFoundException.class,
                () -> service.setAnswerFeedback(
                        actor, messageId, AssistantAnswerSentiment.HELPFUL));

        assertEquals("assistant.answer-not-found", failure.code());
        verify(answerFeedback, never()).save(any());
    }

    @Test
    void rejectsAnInvalidConversationTitleAsBusinessValidation() {
        UUID conversationId = UUID.randomUUID();
        when(conversations.findByIdAndOrganizationIdAndActorUserId(
                        conversationId,
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(ownedConversation(conversationId)));

        assertThrows(
                BusinessValidationException.class,
                () -> service.rename(actor, conversationId, "  "));
    }

    @Test
    void returnsTheFullOwnedTranscriptInPersistedOrder() {
        UUID conversationId = UUID.randomUUID();
        AssistantConversation conversation = ownedConversation(conversationId);
        UUID turnId = UUID.randomUUID();
        AssistantConversationMessage first = new AssistantConversationMessage(
                UUID.randomUUID(),
                conversationId,
                turnId,
                actor.organizationId(),
                actor.userId(),
                AssistantConversationRole.USER,
                "What is the probation policy?",
                NOW);
        AssistantConversationMessage second = new AssistantConversationMessage(
                UUID.randomUUID(),
                conversationId,
                turnId,
                actor.organizationId(),
                actor.userId(),
                AssistantConversationRole.ASSISTANT,
                "The probation period is 60 days. [1]",
                NOW.plusSeconds(1));
        when(conversations.findByIdAndOrganizationIdAndActorUserId(
                        conversationId,
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(conversation));
        when(messages.findAllByConversationIdOrderBySequenceId(conversationId))
                .thenReturn(List.of(first, second));
        AssistantAnswerFeedback savedFeedback = new AssistantAnswerFeedback(
                second.getId(),
                actor.organizationId(),
                actor.userId(),
                AssistantAnswerSentiment.HELPFUL,
                NOW.plusSeconds(2));
        when(answerFeedback.findAllByMessageIdIn(List.of(first.getId(), second.getId())))
                .thenReturn(List.of(savedFeedback));

        List<AssistantConversationMessageView> history =
                service.history(actor, conversationId);

        assertEquals(
                List.of(
                        AssistantConversationRole.USER,
                        AssistantConversationRole.ASSISTANT),
                history.stream()
                        .map(AssistantConversationMessageView::role)
                        .toList());
        assertEquals(
                List.of(
                        "What is the probation policy?",
                        "The probation period is 60 days. [1]"),
                history.stream()
                        .map(AssistantConversationMessageView::content)
                        .toList());
        assertEquals(null, history.get(0).feedback());
        assertEquals(AssistantAnswerSentiment.HELPFUL, history.get(1).feedback());
    }

    @Test
    void listsRecentConversationsWithMessageCountsLoadedInOneQuery() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        AssistantConversation first = ownedConversation(firstId);
        AssistantConversation second = ownedConversation(secondId);
        AssistantConversationMessageRepository.MessageCount firstCount =
                mock(AssistantConversationMessageRepository.MessageCount.class);
        when(firstCount.getConversationId()).thenReturn(firstId);
        when(firstCount.getMessageCount()).thenReturn(4L);
        when(conversations
                        .findTop50ByOrganizationIdAndActorUserIdOrderByLastActivityAtDescIdDesc(
                                actor.organizationId(), actor.userId()))
                .thenReturn(List.of(first, second));
        when(messages.countByConversationIds(List.of(firstId, secondId)))
                .thenReturn(List.of(firstCount));

        List<AssistantConversationSummary> summaries = service.list(actor);

        assertEquals(List.of(4L, 0L), summaries.stream()
                .map(AssistantConversationSummary::messageCount)
                .toList());
        verify(messages).countByConversationIds(List.of(firstId, secondId));
    }

    private AssistantConversation ownedConversation(UUID conversationId) {
        return new AssistantConversation(
                conversationId,
                actor.organizationId(),
                actor.userId(),
                "Probation policy",
                NOW);
    }
}
