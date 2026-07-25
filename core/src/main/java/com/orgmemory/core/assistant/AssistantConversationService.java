package com.orgmemory.core.assistant;

import com.orgmemory.core.organization.CurrentActor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantConversationService {

    private final AssistantConversationRepository conversations;
    private final AssistantConversationMessageRepository messages;
    private final Clock clock;

    AssistantConversationService(
            AssistantConversationRepository conversations,
            AssistantConversationMessageRepository messages,
            Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional
    public UUID beginTurn(CurrentActor actor, UUID requestedId, String userMessage) {
        Instant now = clock.instant();
        AssistantConversation conversation;
        if (requestedId == null) {
            conversation = conversations.save(new AssistantConversation(
                    UUID.randomUUID(),
                    actor.organizationId(),
                    actor.userId(),
                    firstTitle(userMessage),
                    now));
        } else {
            conversation = requireOwned(actor, requestedId);
            conversation.touch(now);
        }
        messages.save(new AssistantConversationMessage(
                conversation.getId(),
                actor.organizationId(),
                actor.userId(),
                AssistantConversationRole.USER,
                userMessage,
                now));
        return conversation.getId();
    }

    @Transactional
    public void completeTurn(CurrentActor actor, UUID conversationId, String assistantMessage) {
        if (assistantMessage == null || assistantMessage.isBlank()) {
            return;
        }
        AssistantConversation conversation = requireOwned(actor, conversationId);
        Instant now = clock.instant();
        messages.save(new AssistantConversationMessage(
                conversationId,
                actor.organizationId(),
                actor.userId(),
                AssistantConversationRole.ASSISTANT,
                assistantMessage,
                now));
        conversation.touch(now);
    }

    @Transactional(readOnly = true)
    public List<AssistantConversationSummary> list(CurrentActor actor) {
        return conversations
                .findTop50ByOrganizationIdAndActorUserIdOrderByLastActivityAtDescIdDesc(
                        actor.organizationId(), actor.userId())
                .stream()
                .map(conversation -> new AssistantConversationSummary(
                        conversation.getId(),
                        conversation.title(),
                        conversation.lastActivityAt(),
                        messages.countByConversationId(conversation.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssistantConversationMessageView> history(
            CurrentActor actor, UUID conversationId) {
        requireOwned(actor, conversationId);
        return messages.findAllByConversationIdOrderBySequenceId(conversationId)
                .stream()
                .map(AssistantConversationMessage::view)
                .toList();
    }

    @Transactional
    public void rename(CurrentActor actor, UUID conversationId, String title) {
        requireOwned(actor, conversationId).rename(title);
    }

    @Transactional
    public void delete(CurrentActor actor, UUID conversationId) {
        conversations.delete(requireOwned(actor, conversationId));
    }

    @Transactional(readOnly = true)
    public void requireAccess(CurrentActor actor, UUID conversationId) {
        requireOwned(actor, conversationId);
    }

    private AssistantConversation requireOwned(CurrentActor actor, UUID conversationId) {
        return conversations
                .findByIdAndOrganizationIdAndActorUserId(
                        conversationId, actor.organizationId(), actor.userId())
                .orElseThrow(() -> new AssistantConversationNotFoundException(conversationId));
    }

    private static String firstTitle(String message) {
        String normalized = message.strip().replaceAll("\\s+", " ");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 77) + "...";
    }
}
