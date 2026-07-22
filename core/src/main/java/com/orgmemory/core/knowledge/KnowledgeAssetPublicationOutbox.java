package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_asset_publication_outbox")
class KnowledgeAssetPublicationOutbox extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "knowledge_space_id", nullable = false, updatable = false)
    private UUID knowledgeSpaceId;

    @Column(name = "source_revision_id", nullable = false, updatable = false)
    private UUID sourceRevisionId;

    @Column(name = "source_object_id", nullable = false, updatable = false)
    private UUID sourceObjectId;

    @Column(name = "knowledge_asset_id", nullable = false, updatable = false)
    private UUID knowledgeAssetId;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Column(name = "projection_generation", nullable = false, updatable = false)
    private long projectionGeneration;

    @Column(name = "embedding_profile_id", nullable = false, updatable = false)
    private UUID embeddingProfileId;

    @Column(name = "embedding_dimensions", nullable = false, updatable = false)
    private int embeddingDimensions;

    @Column(name = "pipeline_version", nullable = false, length = 64, updatable = false)
    private String pipelineVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeAssetPublicationStatus status;

    @Column(name = "attempt_count", nullable = false)
    @SuppressWarnings("unused") // Persisted publication evidence read by operational queries.
    private int attemptCount;

    @Column(name = "authorization_model_id")
    @SuppressWarnings({"unused", "FieldCanBeLocal"}) // Persisted evidence read by operational queries.
    private String authorizationModelId;

    @Column(name = "last_error_code", length = 64)
    @SuppressWarnings("unused") // Persisted publication evidence read by operational queries.
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 512)
    @SuppressWarnings("unused") // Persisted publication evidence read by operational queries.
    private String lastErrorMessage;

    @Column(name = "applied_at")
    @SuppressWarnings({"unused", "FieldCanBeLocal"}) // Persisted evidence read by operational queries.
    private Instant appliedAt;

    protected KnowledgeAssetPublicationOutbox() {
    }

    KnowledgeAssetPublicationOutbox(PublishKnowledgeAssetCommand command, UUID knowledgeAssetId) {
        super(UUID.randomUUID());
        this.organizationId = command.organizationId();
        this.knowledgeSpaceId = command.knowledgeSpaceId();
        this.sourceRevisionId = command.sourceRevisionId();
        this.sourceObjectId = command.sourceObjectId();
        this.knowledgeAssetId = knowledgeAssetId;
        this.ownerUserId = command.ownerUserId();
        this.projectionGeneration = command.projectionGeneration();
        this.embeddingProfileId = command.embeddingProfile().id();
        this.embeddingDimensions = command.embeddingProfile().dimensions();
        this.pipelineVersion = command.pipelineVersion();
        this.status = KnowledgeAssetPublicationStatus.PENDING;
    }

    void requireSamePublication(PublishKnowledgeAssetCommand command, UUID expectedAssetId) {
        if (!organizationId.equals(command.organizationId())
                || !knowledgeSpaceId.equals(command.knowledgeSpaceId())
                || !sourceRevisionId.equals(command.sourceRevisionId())
                || !sourceObjectId.equals(command.sourceObjectId())
                || !knowledgeAssetId.equals(expectedAssetId)
                || !ownerUserId.equals(command.ownerUserId())
                || projectionGeneration != command.projectionGeneration()
                || !embeddingProfileId.equals(command.embeddingProfile().id())
                || embeddingDimensions != command.embeddingProfile().dimensions()
                || !pipelineVersion.equals(command.pipelineVersion())) {
            throw new KnowledgeIngestionConflictException(
                    "The publication generation already exists with different security metadata");
        }
    }

    void startAttempt() {
        if (status == KnowledgeAssetPublicationStatus.PENDING) {
            attemptCount++;
            lastErrorCode = null;
            lastErrorMessage = null;
        }
    }

    void recordFailure(String code, String message) {
        if (status == KnowledgeAssetPublicationStatus.PENDING) {
            lastErrorCode = code;
            lastErrorMessage = SourceFailureMessage.truncate(message);
        }
    }

    void markApplied(String modelId, Instant timestamp) {
        if (status == KnowledgeAssetPublicationStatus.APPLIED) {
            return;
        }
        status = KnowledgeAssetPublicationStatus.APPLIED;
        authorizationModelId = modelId;
        lastErrorCode = null;
        lastErrorMessage = null;
        appliedAt = timestamp;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getKnowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    UUID getSourceRevisionId() {
        return sourceRevisionId;
    }

    UUID getKnowledgeAssetId() {
        return knowledgeAssetId;
    }

    UUID getOwnerUserId() {
        return ownerUserId;
    }

    long getProjectionGeneration() {
        return projectionGeneration;
    }

    KnowledgeAssetPublicationStatus getStatus() {
        return status;
    }
}
