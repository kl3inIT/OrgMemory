package com.orgmemory.core.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permission_audit_events")
public class PermissionAuditEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(nullable = false, length = 64, updatable = false)
    private String operation;

    @Column(name = "resource_type", nullable = false, length = 64, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private PermissionAuditDecision decision;

    @Column(name = "reason_code", nullable = false, length = 128, updatable = false)
    private String reasonCode;

    @Column(name = "policy_version", nullable = false, length = 64, updatable = false)
    private String policyVersion;

    @Column(name = "request_id", length = 128, updatable = false)
    private String requestId;

    @Column(name = "query_fingerprint", length = 64, updatable = false)
    private String queryFingerprint;

    @Column(name = "ingestion_acl_snapshot_id", updatable = false)
    private UUID ingestionAclSnapshotId;

    @Column(name = "current_acl_snapshot_id", updatable = false)
    private UUID currentAclSnapshotId;

    @Column(name = "authorization_model_id", updatable = false)
    private String authorizationModelId;

    @Column(name = "source_revision_id", updatable = false)
    private UUID sourceRevisionId;

    @Column(name = "knowledge_chunk_id", updatable = false)
    private UUID knowledgeChunkId;

    @Column(name = "embedding_profile_id", updatable = false)
    private UUID embeddingProfileId;

    @Column(name = "projection_generation", updatable = false)
    private Long projectionGeneration;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected PermissionAuditEvent() {
    }

    PermissionAuditEvent(
            UUID id,
            UUID organizationId,
            UUID actorUserId,
            String operation,
            String resourceType,
            String resourceId,
            PermissionAuditDecision decision,
            String reasonCode,
            String policyVersion,
            String requestId,
            String queryFingerprint,
            UUID ingestionAclSnapshotId,
            UUID currentAclSnapshotId,
            String authorizationModelId,
            UUID sourceRevisionId,
            UUID knowledgeChunkId,
            UUID embeddingProfileId,
            Long projectionGeneration,
            Instant occurredAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.actorUserId = actorUserId;
        this.operation = operation;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.decision = decision;
        this.reasonCode = reasonCode;
        this.policyVersion = policyVersion;
        this.requestId = requestId;
        this.queryFingerprint = queryFingerprint;
        this.ingestionAclSnapshotId = ingestionAclSnapshotId;
        this.currentAclSnapshotId = currentAclSnapshotId;
        this.authorizationModelId = authorizationModelId;
        this.sourceRevisionId = sourceRevisionId;
        this.knowledgeChunkId = knowledgeChunkId;
        this.embeddingProfileId = embeddingProfileId;
        this.projectionGeneration = projectionGeneration;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getOperation() {
        return operation;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public PermissionAuditDecision getDecision() {
        return decision;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getQueryFingerprint() {
        return queryFingerprint;
    }

    public UUID getIngestionAclSnapshotId() {
        return ingestionAclSnapshotId;
    }

    public UUID getCurrentAclSnapshotId() {
        return currentAclSnapshotId;
    }

    public String getAuthorizationModelId() {
        return authorizationModelId;
    }

    public UUID getSourceRevisionId() {
        return sourceRevisionId;
    }

    public UUID getKnowledgeChunkId() {
        return knowledgeChunkId;
    }

    public UUID getEmbeddingProfileId() {
        return embeddingProfileId;
    }

    public Long getProjectionGeneration() {
        return projectionGeneration;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
