package com.orgmemory.core.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.EffectiveAuthorizationService;
import com.orgmemory.core.knowledge.evidence.GovernedEvidenceQuery;
import com.orgmemory.core.knowledge.evidence.GovernedEvidenceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.error.BusinessConflictException;
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

class AssistantEvidenceServiceTests {

    private final AssistantConversationRepository conversations =
            mock(AssistantConversationRepository.class);
    private final AssistantEvidenceBindingRepository bindings =
            mock(AssistantEvidenceBindingRepository.class);
    private final AssistantTurnEvidenceBindingRepository turnBindings =
            mock(AssistantTurnEvidenceBindingRepository.class);
    private final GovernedEvidenceQuery sources =
            mock(GovernedEvidenceQuery.class);
    private final EffectiveAuthorizationService authorization =
            mock(EffectiveAuthorizationService.class);
    private final AssistantEvidenceAnswerabilityPort answerability =
            mock(AssistantEvidenceAnswerabilityPort.class);
    private final CurrentActor actor = new CurrentActor(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Laura",
            "laura@example.test");
    private final UUID conversationId = UUID.randomUUID();
    private final UUID bindingId = UUID.randomUUID();
    private final UUID sourceObjectId = UUID.randomUUID();
    private final UUID sourceRevisionId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private AssistantEvidenceService service;
    private AssistantEvidenceBinding binding;

    @BeforeEach
    void setUp() {
        service = new AssistantEvidenceService(
                conversations,
                bindings,
                turnBindings,
                sources,
                authorization,
                answerability,
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC));
        binding = new AssistantEvidenceBinding(
                bindingId,
                actor.organizationId(),
                conversationId,
                actor.userId(),
                sourceObjectId,
                sourceRevisionId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void claimsOnlyTheExactReadyRevisionAndPersistsTheTurnSelection() {
        GovernedEvidenceRef source = readySource();
        UUID secondBindingId = UUID.randomUUID();
        UUID secondSourceObjectId = UUID.randomUUID();
        UUID secondSourceRevisionId = UUID.randomUUID();
        UUID secondAssetId = UUID.randomUUID();
        AssistantEvidenceBinding secondBinding = new AssistantEvidenceBinding(
                secondBindingId,
                actor.organizationId(),
                conversationId,
                actor.userId(),
                secondSourceObjectId,
                secondSourceRevisionId);
        GovernedEvidenceRef secondSource = source(
                GovernedEvidenceRef.ProcessingState.READY,
                true,
                true,
                true,
                secondSourceObjectId,
                secondSourceRevisionId,
                secondAssetId,
                null);
        when(bindings.findAllByIdInAndOrganizationIdAndConversationIdAndCreatedByUserId(
                        List.of(bindingId, secondBindingId),
                        actor.organizationId(),
                        conversationId,
                        actor.userId()))
                .thenReturn(List.of(secondBinding, binding));
        when(sources.find(actor.organizationId(), sourceObjectId, sourceRevisionId))
                .thenReturn(Optional.of(source));
        when(sources.find(
                        actor.organizationId(),
                        secondSourceObjectId,
                        secondSourceRevisionId))
                .thenReturn(Optional.of(secondSource));
        when(authorization.authorize(any(), any(), any(), any()))
                .thenReturn(AuthorizationDecision.allow("model-1"));
        when(answerability.evaluate(source))
                .thenReturn(AssistantEvidenceAnswerabilityPort.Answerability.ready());
        when(answerability.evaluate(secondSource))
                .thenReturn(AssistantEvidenceAnswerabilityPort.Answerability.ready());

        UUID turnId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();

        var selection = service.claimForTurn(
                actor,
                conversationId,
                turnId,
                userMessageId,
                List.of(bindingId, secondBindingId));

        assertTrue(selection.restricted());
        assertEquals(List.of(bindingId, secondBindingId), selection.items().stream()
                .map(item -> item.bindingId())
                .toList());
        assertTrue(selection.contains(assetId, sourceObjectId, sourceRevisionId));
        assertTrue(selection.contains(
                secondAssetId,
                secondSourceObjectId,
                secondSourceRevisionId));
        ArgumentCaptor<List<AssistantTurnEvidenceBinding>> saved =
                ArgumentCaptor.forClass(List.class);
        verify(turnBindings).saveAll(saved.capture());
        assertEquals(List.of(bindingId, secondBindingId), saved.getValue().stream()
                .map(AssistantTurnEvidenceBinding::bindingId)
                .toList());
        assertEquals(List.of(1, 2), saved.getValue().stream()
                .map(AssistantTurnEvidenceBinding::ordinal)
                .toList());
        assertTrue(saved.getValue().stream().allMatch(claim -> claim.turnId().equals(turnId)));
        assertTrue(saved.getValue().stream()
                .allMatch(claim -> claim.userMessageId().equals(userMessageId)));
    }

    @Test
    void refusesASelectedBindingWhenTheActiveEngineIsStillIndexing() {
        GovernedEvidenceRef source = readySource();
        when(bindings.findAllByIdInAndOrganizationIdAndConversationIdAndCreatedByUserId(
                        any(), any(), any(), any()))
                .thenReturn(List.of(binding));
        when(sources.find(actor.organizationId(), sourceObjectId, sourceRevisionId))
                .thenReturn(Optional.of(source));
        when(authorization.authorize(any(), any(), any(), any()))
                .thenReturn(AuthorizationDecision.allow("model-1"));
        when(answerability.evaluate(source))
                .thenReturn(AssistantEvidenceAnswerabilityPort.Answerability.indexing());

        assertThrows(
                BusinessConflictException.class,
                () -> service.claimForTurn(
                        actor,
                        conversationId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        List.of(bindingId)));
    }

    @Test
    void revokedAccessBecomesUnavailableAndCannotBeClaimed() {
        GovernedEvidenceRef source = readySource();
        when(bindings.findByIdAndOrganizationIdAndConversationIdAndCreatedByUserId(
                        bindingId,
                        actor.organizationId(),
                        conversationId,
                        actor.userId()))
                .thenReturn(Optional.of(binding));
        when(conversations.findByIdAndOrganizationIdAndActorUserId(
                        conversationId,
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(mock(AssistantConversation.class)));
        when(sources.find(actor.organizationId(), sourceObjectId, sourceRevisionId))
                .thenReturn(Optional.of(source));
        when(authorization.authorize(any(), any(), any(), any()))
                .thenReturn(AuthorizationDecision.deny("RELATIONSHIP_DENIED", "model-1"));

        assertUnavailableWithoutMetadata(service.get(actor, conversationId, bindingId));
        verifyNoInteractions(answerability);
    }

    @Test
    void revokedAccessHidesMetadataBeforeEveryPersistedSourceState() {
        when(bindings.findByIdAndOrganizationIdAndConversationIdAndCreatedByUserId(
                        bindingId,
                        actor.organizationId(),
                        conversationId,
                        actor.userId()))
                .thenReturn(Optional.of(binding));
        when(conversations.findByIdAndOrganizationIdAndActorUserId(
                        conversationId,
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(mock(AssistantConversation.class)));
        when(sources.find(actor.organizationId(), sourceObjectId, sourceRevisionId))
                .thenReturn(Optional.of(source(
                        GovernedEvidenceRef.ProcessingState.PROCESSING,
                        true,
                        true,
                        true,
                        assetId,
                        "processing_detail")))
                .thenReturn(Optional.of(source(
                        GovernedEvidenceRef.ProcessingState.FAILED,
                        true,
                        true,
                        true,
                        assetId,
                        "parse_failed")))
                .thenReturn(Optional.of(source(
                        GovernedEvidenceRef.ProcessingState.READY,
                        false,
                        true,
                        true,
                        assetId,
                        "retired_detail")));
        when(authorization.authorize(any(), any(), any(), any()))
                .thenReturn(AuthorizationDecision.deny("RELATIONSHIP_DENIED", "model-1"));

        assertUnavailableWithoutMetadata(service.get(actor, conversationId, bindingId));
        assertUnavailableWithoutMetadata(service.get(actor, conversationId, bindingId));
        assertUnavailableWithoutMetadata(service.get(actor, conversationId, bindingId));
        verifyNoInteractions(answerability);
    }

    @Test
    void preAssetProcessingMetadataRemainsVisibleOnlyThroughTheActorOwnedBinding() {
        when(bindings.findByIdAndOrganizationIdAndConversationIdAndCreatedByUserId(
                        bindingId,
                        actor.organizationId(),
                        conversationId,
                        actor.userId()))
                .thenReturn(Optional.of(binding));
        when(conversations.findByIdAndOrganizationIdAndActorUserId(
                        conversationId,
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(mock(AssistantConversation.class)));
        when(sources.find(actor.organizationId(), sourceObjectId, sourceRevisionId))
                .thenReturn(Optional.of(source(
                        GovernedEvidenceRef.ProcessingState.PROCESSING,
                        true,
                        true,
                        true,
                        null,
                        null)));

        AssistantEvidenceBindingView view = service.get(actor, conversationId, bindingId);

        assertEquals(AssistantEvidenceStatus.PROCESSING, view.status());
        assertEquals("Policy", view.title());
        assertEquals("policy.pdf", view.fileName());
        verifyNoInteractions(authorization, answerability);
    }

    @Test
    void anotherConversationBindingIsOpaqueNotFound() {
        when(conversations.findByIdAndOrganizationIdAndActorUserId(
                        conversationId,
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(mock(AssistantConversation.class)));

        assertThrows(
                BusinessNotFoundException.class,
                () -> service.get(actor, conversationId, bindingId));
    }

    @Test
    void anotherOrganizationIsOpaqueNotFound() {
        CurrentActor otherOrganizationActor = new CurrentActor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                actor.userId(),
                "Laura",
                "laura@example.test");

        assertThrows(
                BusinessNotFoundException.class,
                () -> service.get(otherOrganizationActor, conversationId, bindingId));
    }

    @Test
    void reportsProcessingFailedAndRetiredSourceStatesBeforeAnswerability() {
        when(conversations.findByIdAndOrganizationIdAndActorUserId(
                        conversationId,
                        actor.organizationId(),
                        actor.userId()))
                .thenReturn(Optional.of(mock(AssistantConversation.class)));
        when(bindings.findByIdAndOrganizationIdAndConversationIdAndCreatedByUserId(
                        bindingId,
                        actor.organizationId(),
                        conversationId,
                        actor.userId()))
                .thenReturn(Optional.of(binding));
        when(sources.find(actor.organizationId(), sourceObjectId, sourceRevisionId))
                .thenReturn(Optional.of(source(
                        GovernedEvidenceRef.ProcessingState.PROCESSING,
                        true,
                        true,
                        true,
                        null,
                        null)))
                .thenReturn(Optional.of(source(
                        GovernedEvidenceRef.ProcessingState.FAILED,
                        true,
                        true,
                        true,
                        null,
                        "parse_failed")))
                .thenReturn(Optional.of(source(
                        GovernedEvidenceRef.ProcessingState.READY,
                        false,
                        true,
                        true,
                        assetId,
                        null)));
        when(authorization.authorize(any(), any(), any(), any()))
                .thenReturn(AuthorizationDecision.allow("model-1"));

        assertEquals(
                AssistantEvidenceStatus.PROCESSING,
                service.get(actor, conversationId, bindingId).status());
        assertEquals(
                AssistantEvidenceStatus.FAILED,
                service.get(actor, conversationId, bindingId).status());
        assertEquals(
                AssistantEvidenceStatus.UNAVAILABLE,
                service.get(actor, conversationId, bindingId).status());
        verifyNoInteractions(answerability);
    }

    private GovernedEvidenceRef readySource() {
        return source(
                GovernedEvidenceRef.ProcessingState.READY,
                true,
                true,
                true,
                assetId,
                null);
    }

    private GovernedEvidenceRef source(
            GovernedEvidenceRef.ProcessingState state,
            boolean active,
            boolean latest,
            boolean current,
            UUID knowledgeAssetId,
            String failureCode) {
        return source(
                state,
                active,
                latest,
                current,
                sourceObjectId,
                sourceRevisionId,
                knowledgeAssetId,
                failureCode);
    }

    private GovernedEvidenceRef source(
            GovernedEvidenceRef.ProcessingState state,
            boolean active,
            boolean latest,
            boolean current,
            UUID selectedSourceObjectId,
            UUID selectedSourceRevisionId,
            UUID knowledgeAssetId,
            String failureCode) {
        return new GovernedEvidenceRef(
                actor.organizationId(),
                UUID.randomUUID(),
                selectedSourceObjectId,
                selectedSourceRevisionId,
                state,
                active,
                latest,
                current,
                knowledgeAssetId,
                UUID.randomUUID(),
                "Policy",
                "policy.pdf",
                failureCode);
    }

    private static void assertUnavailableWithoutMetadata(AssistantEvidenceBindingView view) {
        assertEquals(AssistantEvidenceStatus.UNAVAILABLE, view.status());
        assertEquals("Unavailable evidence", view.title());
        assertEquals("Unavailable file", view.fileName());
        assertNull(view.knowledgeAssetId());
        assertNull(view.failureCode());
    }
}
