package com.orgmemory.core.assistant;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assistant_turn_files")
class AssistantTurnFile extends BaseEntity {

    @Column(name = "turn_id", nullable = false, updatable = false)
    private UUID turnId;
    @Column(name = "user_message_id", nullable = false, updatable = false)
    private UUID userMessageId;
    @Column(name = "assistant_file_id", nullable = false, updatable = false)
    private UUID assistantFileId;
    @Column(name = "processing_generation", nullable = false, updatable = false)
    private long processingGeneration;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;
    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;
    @Column(nullable = false, updatable = false)
    private int ordinal;

    protected AssistantTurnFile() {}

    AssistantTurnFile(
            UUID id,
            UUID turnId,
            UUID userMessageId,
            UUID assistantFileId,
            long processingGeneration,
            UUID organizationId,
            UUID conversationId,
            UUID actorUserId,
            int ordinal) {
        super(Objects.requireNonNull(id, "id"));
        if (ordinal < 1 || ordinal > 3) throw new IllegalArgumentException("ordinal must be 1..3");
        this.turnId = Objects.requireNonNull(turnId, "turnId");
        this.userMessageId = Objects.requireNonNull(userMessageId, "userMessageId");
        this.assistantFileId = Objects.requireNonNull(assistantFileId, "assistantFileId");
        this.processingGeneration = processingGeneration;
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.ordinal = ordinal;
    }
}
