package com.orgmemory.core.assistant;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.ai.AssistantModelSelectionRef;
import com.orgmemory.core.shared.error.BusinessErrorExposure;
import com.orgmemory.core.shared.error.BusinessNotFoundException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantConversationService implements AssistantTranscriptContext {

    private final AssistantConversationRepository conversations;
    private final AssistantConversationMessageRepository messages;
    private final AssistantMessageCitationRepository citations;
    private final AssistantAnswerFeedbackRepository answerFeedback;
    private final Clock clock;

    AssistantConversationService(
            AssistantConversationRepository conversations,
            AssistantConversationMessageRepository messages,
            AssistantMessageCitationRepository citations,
            AssistantAnswerFeedbackRepository answerFeedback,
            Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.citations = citations;
        this.answerFeedback = answerFeedback;
        this.clock = clock;
    }

    @Transactional
    public AssistantTurnRef beginTurn(
            CurrentActor actor, UUID requestedId, String userMessage) {
        return beginTurn(actor, requestedId, userMessage, null);
    }

    @Transactional
    public AssistantTurnRef beginTurn(
            CurrentActor actor,
            UUID requestedId,
            String userMessage,
            AssistantModelSelectionRef modelSelection) {
        String validUserMessage = requireUserMessage(userMessage);
        Instant now = clock.instant();
        AssistantConversation conversation;
        if (requestedId == null) {
            conversation = conversations.save(new AssistantConversation(
                    UUID.randomUUID(),
                    actor.organizationId(),
                    actor.userId(),
                    firstTitle(validUserMessage),
                    now));
        } else {
            conversation = requireOwnedForUpdate(actor, requestedId);
            conversation.touch(now);
        }
        conversation.selectModel(modelSelection);
        UUID turnId = UUID.randomUUID();
        messages.save(new AssistantConversationMessage(
                UUID.randomUUID(),
                conversation.getId(),
                turnId,
                actor.organizationId(),
                actor.userId(),
                AssistantConversationRole.USER,
                validUserMessage,
                now));
        return new AssistantTurnRef(conversation.getId(), turnId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssistantContextMessage> recentCompletedTurns(
            UUID organizationId, UUID conversationId, int maxTurns) {
        if (organizationId == null || conversationId == null || maxTurns <= 0) {
            return List.of();
        }
        List<UUID> turnIds = messages.findRecentCompletedTurnIds(
                organizationId, conversationId, PageRequest.of(0, maxTurns));
        if (turnIds.isEmpty()) {
            return List.of();
        }
        // Sequence order alone would interleave overlapping turns. Grouping by
        // turn first restores question-then-answer within each turn, and the
        // question's sequence orders the turns, because beginTurn always
        // commits before the answer of the same turn exists.
        Map<UUID, List<AssistantConversationMessage>> byTurn = new LinkedHashMap<>();
        for (AssistantConversationMessage message
                : messages.findAllByTurnIdInOrderBySequenceId(turnIds)) {
            byTurn.computeIfAbsent(message.turnId(), turn -> new ArrayList<>()).add(message);
        }
        List<AssistantContextMessage> context = new ArrayList<>();
        for (List<AssistantConversationMessage> turn : byTurn.values()) {
            // Within a turn, sequence order is already question then answer.
            for (AssistantConversationMessage message : turn) {
                context.add(new AssistantContextMessage(message.role(), message.content()));
            }
        }
        return List.copyOf(context);
    }

    @Transactional(readOnly = true)
    public AssistantModelSelectionRef modelSelection(
            CurrentActor actor,
            UUID conversationId) {
        return requireOwned(actor, conversationId).modelSelection();
    }

    @Transactional
    public void selectModel(
            CurrentActor actor,
            UUID conversationId,
            AssistantModelSelectionRef modelSelection) {
        requireOwnedForUpdate(actor, conversationId).selectModel(modelSelection);
    }

    @Transactional
    public void completeTurn(
            CurrentActor actor,
            AssistantTurnRef turn,
            UUID assistantMessageId,
            String assistantMessage) {
        completeTurn(actor, turn, assistantMessageId, assistantMessage, List.of());
    }

    @Transactional
    public void completeTurn(
            CurrentActor actor,
            AssistantTurnRef turn,
            UUID assistantMessageId,
            String assistantMessage,
            List<AssistantCitation> answerCitations) {
        if (assistantMessage == null || assistantMessage.isBlank()) {
            return;
        }
        List<AssistantCitation> persistedCitations = validateCitations(answerCitations);
        UUID conversationId = turn.conversationId();
        AssistantConversation conversation = requireOwned(actor, conversationId);
        Instant now = clock.instant();
        messages.save(new AssistantConversationMessage(
                assistantMessageId,
                conversationId,
                turn.turnId(),
                actor.organizationId(),
                actor.userId(),
                AssistantConversationRole.ASSISTANT,
                assistantMessage,
                now));
        citations.saveAll(persistedCitations.stream()
                .map(citation -> new AssistantMessageCitation(
                        UUID.randomUUID(),
                        assistantMessageId,
                        actor.organizationId(),
                        actor.userId(),
                        citation.number(),
                        citation.evidence().chunkId()))
                .toList());
        conversation.touch(now);
    }

    @Transactional(readOnly = true)
    public List<AssistantCitationReference> citationReferences(
            CurrentActor actor, UUID messageId) {
        messages.findOneByIdAndOrganizationIdAndActorUserIdAndRole(
                        messageId,
                        actor.organizationId(),
                        actor.userId(),
                        AssistantConversationRole.ASSISTANT)
                .orElseThrow(() -> new BusinessNotFoundException(
                        "assistant.answer-not-found",
                        "Assistant answer not found",
                        BusinessErrorExposure.OPAQUE_RESOURCE));
        List<AssistantMessageCitation> stored =
                citations.findAllByMessageIdOrderByCitationNumber(messageId);
        if (stored.size() > 100) {
            throw new IllegalStateException("Assistant citation reference limit exceeded");
        }
        return stored.stream().map(AssistantMessageCitation::view).toList();
    }

    @Transactional(readOnly = true)
    public List<AssistantConversationSummary> list(CurrentActor actor) {
        List<AssistantConversation> recent = conversations
                .findTop50ByOrganizationIdAndActorUserIdOrderByLastActivityAtDescIdDesc(
                        actor.organizationId(), actor.userId());
        if (recent.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> messageCounts = messages
                .countByConversationIds(recent.stream()
                        .map(AssistantConversation::getId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        AssistantConversationMessageRepository.MessageCount::getConversationId,
                        AssistantConversationMessageRepository.MessageCount::getMessageCount));
        return recent.stream()
                .map(conversation -> new AssistantConversationSummary(
                        conversation.getId(),
                        conversation.title(),
                        conversation.lastActivityAt(),
                        messageCounts.getOrDefault(conversation.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssistantConversationMessageView> history(
            CurrentActor actor, UUID conversationId) {
        requireOwned(actor, conversationId);
        List<AssistantConversationMessage> transcript =
                messages.findAllByConversationIdOrderBySequenceId(conversationId);
        if (transcript.isEmpty()) {
            return List.of();
        }
        Map<UUID, AssistantAnswerSentiment> feedbackByMessage = answerFeedback
                .findAllByMessageIdIn(transcript.stream()
                        .map(AssistantConversationMessage::getId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        feedback -> feedback.view().messageId(),
                        AssistantAnswerFeedback::sentiment));
        return transcript.stream()
                .map(message -> message.view(feedbackByMessage.get(message.getId())))
                .toList();
    }

    @Transactional
    public AssistantAnswerFeedbackView setAnswerFeedback(
            CurrentActor actor,
            UUID messageId,
            AssistantAnswerSentiment sentiment) {
        requireOwnedAssistantMessage(actor, messageId);
        Instant now = clock.instant();
        AssistantAnswerFeedback feedback = answerFeedback.findById(messageId)
                .map(current -> {
                    current.update(sentiment, now);
                    return current;
                })
                .orElseGet(() -> new AssistantAnswerFeedback(
                        messageId,
                        actor.organizationId(),
                        actor.userId(),
                        sentiment,
                        now));
        return answerFeedback.save(feedback).view();
    }

    @Transactional
    public void deleteAnswerFeedback(CurrentActor actor, UUID messageId) {
        requireOwnedAssistantMessage(actor, messageId);
        answerFeedback.findById(messageId).ifPresent(answerFeedback::delete);
    }

    @Transactional
    public void rename(CurrentActor actor, UUID conversationId, String title) {
        requireOwned(actor, conversationId).rename(requireTitle(title));
    }

    @Transactional
    public void delete(CurrentActor actor, UUID conversationId) {
        conversations.delete(requireOwned(actor, conversationId));
    }

    private AssistantConversation requireOwned(CurrentActor actor, UUID conversationId) {
        return conversations
                .findByIdAndOrganizationIdAndActorUserId(
                        conversationId, actor.organizationId(), actor.userId())
                .orElseThrow(() -> new AssistantConversationNotFoundException(conversationId));
    }

    private AssistantConversation requireOwnedForUpdate(
            CurrentActor actor,
            UUID conversationId) {
        return conversations
                .findForUpdateByIdAndOrganizationIdAndActorUserId(
                        conversationId,
                        actor.organizationId(),
                        actor.userId())
                .orElseThrow(() -> new AssistantConversationNotFoundException(conversationId));
    }

    private AssistantConversationMessage requireOwnedAssistantMessage(
            CurrentActor actor, UUID messageId) {
        return messages.findByIdAndOrganizationIdAndActorUserIdAndRole(
                        messageId,
                        actor.organizationId(),
                        actor.userId(),
                        AssistantConversationRole.ASSISTANT)
                .orElseThrow(() -> new BusinessNotFoundException(
                        "assistant.answer-not-found",
                        "Assistant answer not found",
                        BusinessErrorExposure.OPAQUE_RESOURCE));
    }

    private static String firstTitle(String message) {
        String normalized = message.strip().replaceAll("\\s+", " ");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 77) + "...";
    }

    private static List<AssistantCitation> validateCitations(
            List<AssistantCitation> answerCitations) {
        List<AssistantCitation> values = List.copyOf(
                answerCitations == null ? List.of() : answerCitations);
        if (values.size() > 100) {
            throw new IllegalArgumentException("At most 100 citations may be persisted per answer");
        }
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).number() != index + 1) {
                throw new IllegalArgumentException(
                        "Citation numbers must be consecutive and ordered");
            }
        }
        return values;
    }

    private static String requireUserMessage(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > 4_000) {
            throw new BusinessValidationException(
                    "assistant.message-invalid",
                    "Assistant message must contain between 1 and 4000 characters");
        }
        return normalized;
    }

    private static String requireTitle(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw new BusinessValidationException(
                    "assistant.conversation-title-invalid",
                    "Conversation title must contain between 1 and 120 characters");
        }
        return normalized;
    }
}
