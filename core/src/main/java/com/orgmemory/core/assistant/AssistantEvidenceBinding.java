package com.orgmemory.core.assistant;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assistant_evidence_bindings")
class AssistantEvidenceBinding extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "source_object_id", nullable = false, updatable = false)
    private UUID sourceObjectId;

    @Column(name = "source_revision_id", nullable = false, updatable = false)
    private UUID sourceRevisionId;

    protected AssistantEvidenceBinding() {
    }

    AssistantEvidenceBinding(
            UUID id,
            UUID organizationId,
            UUID conversationId,
            UUID createdByUserId,
            UUID sourceObjectId,
            UUID sourceRevisionId) {
        super(Objects.requireNonNull(id, "id"));
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.createdByUserId = Objects.requireNonNull(createdByUserId, "createdByUserId");
        this.sourceObjectId = Objects.requireNonNull(sourceObjectId, "sourceObjectId");
        this.sourceRevisionId = Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
    }

    UUID organizationId() {
        return organizationId;
    }

    UUID conversationId() {
        return conversationId;
    }

    UUID createdByUserId() {
        return createdByUserId;
    }

    UUID sourceObjectId() {
        return sourceObjectId;
    }

    UUID sourceRevisionId() {
        return sourceRevisionId;
    }
}
