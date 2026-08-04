package com.orgmemory.core.assistant;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assistant_message_citations")
class AssistantMessageCitation extends BaseEntity {

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "citation_number", nullable = false, updatable = false)
    private int citationNumber;

    @Column(name = "chunk_id", nullable = false, updatable = false)
    private UUID chunkId;

    protected AssistantMessageCitation() {
    }

    AssistantMessageCitation(
            UUID id,
            UUID messageId,
            UUID organizationId,
            UUID actorUserId,
            int citationNumber,
            UUID chunkId) {
        super(Objects.requireNonNull(id, "id"));
        this.messageId = Objects.requireNonNull(messageId, "messageId");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        if (citationNumber < 1 || citationNumber > 100) {
            throw new IllegalArgumentException("citationNumber must be between 1 and 100");
        }
        this.citationNumber = citationNumber;
        this.chunkId = Objects.requireNonNull(chunkId, "chunkId");
    }

    AssistantCitationReference view() {
        return new AssistantCitationReference(citationNumber, chunkId);
    }
}
