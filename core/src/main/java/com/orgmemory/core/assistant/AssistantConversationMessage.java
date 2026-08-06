package com.orgmemory.core.assistant;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assistant_conversation_messages")
class AssistantConversationMessage extends BaseEntity {

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    /** Null only on rows written before turn identity existed. */
    @Column(name = "turn_id", updatable = false)
    private UUID turnId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private AssistantConversationRole role;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String content;

    @Column(name = "sequence_id", insertable = false, updatable = false)
    private long sequenceId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AssistantConversationMessage() {
    }

    AssistantConversationMessage(
            UUID id,
            UUID conversationId,
            UUID turnId,
            UUID organizationId,
            UUID actorUserId,
            AssistantConversationRole role,
            String content,
            Instant occurredAt) {
        super(Objects.requireNonNull(id, "id"));
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.turnId = Objects.requireNonNull(turnId, "turnId");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.role = Objects.requireNonNull(role, "role");
        this.content = requireContent(content);
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    UUID turnId() {
        return turnId;
    }

    AssistantConversationRole role() {
        return role;
    }

    String content() {
        return content;
    }

    AssistantConversationMessageView view() {
        return new AssistantConversationMessageView(
                getId(), role, content, sequenceId, occurredAt, null);
    }

    AssistantConversationMessageView view(AssistantAnswerSentiment feedback) {
        return new AssistantConversationMessageView(
                getId(), role, content, sequenceId, occurredAt, feedback);
    }

    private static String requireContent(String value) {
        String normalized = Objects.requireNonNull(value, "content").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Conversation message content is required");
        }
        if (normalized.length() > 200_000) {
            throw new IllegalArgumentException("Conversation message content is too long");
        }
        return normalized;
    }
}
