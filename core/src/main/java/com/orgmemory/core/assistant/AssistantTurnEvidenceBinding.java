package com.orgmemory.core.assistant;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assistant_turn_evidence_bindings")
class AssistantTurnEvidenceBinding extends BaseEntity {

    @Column(name = "turn_id", nullable = false, updatable = false)
    private UUID turnId;

    @Column(name = "user_message_id", nullable = false, updatable = false)
    private UUID userMessageId;

    @Column(name = "binding_id", nullable = false, updatable = false)
    private UUID bindingId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(nullable = false, updatable = false)
    private int ordinal;

    protected AssistantTurnEvidenceBinding() {
    }

    AssistantTurnEvidenceBinding(
            UUID id,
            UUID turnId,
            UUID userMessageId,
            UUID bindingId,
            UUID organizationId,
            UUID conversationId,
            UUID actorUserId,
            int ordinal) {
        super(Objects.requireNonNull(id, "id"));
        if (ordinal < 1 || ordinal > 3) {
            throw new IllegalArgumentException("evidence ordinal must be between one and three");
        }
        this.turnId = Objects.requireNonNull(turnId, "turnId");
        this.userMessageId = Objects.requireNonNull(userMessageId, "userMessageId");
        this.bindingId = Objects.requireNonNull(bindingId, "bindingId");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.ordinal = ordinal;
    }

    UUID turnId() {
        return turnId;
    }

    UUID userMessageId() {
        return userMessageId;
    }

    UUID bindingId() {
        return bindingId;
    }

    int ordinal() {
        return ordinal;
    }
}
