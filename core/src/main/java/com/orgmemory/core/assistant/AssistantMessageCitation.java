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

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "evidence_kind", nullable = false, updatable = false)
    private AssistantCitationEvidence.Kind evidenceKind;

    @Column(name = "assistant_file_id", updatable = false)
    private UUID assistantFileId;

    @Column(name = "processing_generation", updatable = false)
    private Long processingGeneration;

    protected AssistantMessageCitation() {
    }

    AssistantMessageCitation(
            UUID id,
            UUID messageId,
            UUID organizationId,
            UUID actorUserId,
            int citationNumber,
            AssistantCitationEvidence.Kind evidenceKind,
            UUID chunkId,
            UUID assistantFileId,
            Long processingGeneration) {
        super(Objects.requireNonNull(id, "id"));
        this.messageId = Objects.requireNonNull(messageId, "messageId");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        if (citationNumber < 1 || citationNumber > 100) {
            throw new IllegalArgumentException("citationNumber must be between 1 and 100");
        }
        this.citationNumber = citationNumber;
        this.evidenceKind = Objects.requireNonNull(evidenceKind, "evidenceKind");
        this.chunkId = Objects.requireNonNull(chunkId, "chunkId");
        this.assistantFileId = assistantFileId;
        this.processingGeneration = processingGeneration;
    }

    AssistantMessageCitation(
            UUID id,
            UUID messageId,
            UUID organizationId,
            UUID actorUserId,
            int citationNumber,
            UUID chunkId) {
        this(id, messageId, organizationId, actorUserId, citationNumber,
                AssistantCitationEvidence.Kind.KNOWLEDGE, chunkId, null, null);
    }

    AssistantCitationReference view() {
        return new AssistantCitationReference(
                citationNumber, evidenceKind, chunkId, assistantFileId, processingGeneration);
    }
}
