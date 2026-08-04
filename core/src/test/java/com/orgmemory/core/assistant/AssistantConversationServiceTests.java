package com.orgmemory.core.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private final AssistantAnswerFeedbackRepository answerFeedback =
            mock(AssistantAnswerFeedbackRepository.class);
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
                answerFeedback,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAnActorOwnedConversationAndStoresTheRawUserTurn() {
        UUID conversationId = service.beginTurn(
                actor,
                null,
                "  How do I submit an expense claim?  ");

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
    }

    @Test
    void rejectsAnotherActorsConversationWithoutWritingAUserTurn() {
        UUID conversationId = UUID.randomUUID();
        when(conversations.findByIdAndOrganizationIdAndActorUserId(
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
    void persistsTheServerAllocatedAssistantMessageIdentity() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(conversations.findByIdAndOrganizationIdAndActorUserId(
                        conversationId,
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(ownedConversation(conversationId)));

        service.completeTurn(
                actor,
                conversationId,
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
    void createsReplacesAndRemovesFeedbackForAnOwnedAssistantAnswer() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        AssistantConversationMessage answer = new AssistantConversationMessage(
                messageId,
                conversationId,
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
        AssistantConversationMessage first = new AssistantConversationMessage(
                UUID.randomUUID(),
                conversationId,
                actor.organizationId(),
                actor.userId(),
                AssistantConversationRole.USER,
                "What is the probation policy?",
                NOW);
        AssistantConversationMessage second = new AssistantConversationMessage(
                UUID.randomUUID(),
                conversationId,
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
