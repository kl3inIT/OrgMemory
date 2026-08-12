package com.orgmemory.core.assistant;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assistant_files")
class AssistantFile extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "media_type", nullable = false, updatable = false)
    private String mediaType;

    @Column(name = "content_length", nullable = false, updatable = false)
    private long contentLength;

    @Column(name = "content_sha256", nullable = false, updatable = false)
    private String contentSha256;

    @Column(name = "object_key", nullable = false, updatable = false)
    private String objectKey;

    @Column(name = "object_etag", updatable = false)
    private String objectEtag;

    @Column(name = "storage_version", updatable = false)
    private String storageVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssistantFileStatus status;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "cleanup_completed_at")
    private Instant cleanupCompletedAt;

    @Column(name = "processing_generation", nullable = false, updatable = false)
    private long processingGeneration;

    @Column(name = "requested_profile_canonical")
    private String requestedProfileCanonical;

    @Column(name = "requested_profile_sha256")
    private String requestedProfileSha256;

    @Column(name = "resolved_profile_canonical")
    private String resolvedProfileCanonical;

    @Column(name = "resolved_profile_sha256")
    private String resolvedProfileSha256;

    @Column(name = "embedding_profile_id")
    private UUID embeddingProfileId;

    @Column(name = "embedding_dimensions")
    private Integer embeddingDimensions;

    @Column(name = "processing_attempt", nullable = false)
    private int processingAttempt;

    @Column(name = "claim_owner")
    private String claimOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    protected AssistantFile() {}

    AssistantFile(
            UUID id,
            UUID organizationId,
            UUID actorUserId,
            String fileName,
            String mediaType,
            long contentLength,
            String contentSha256,
            String objectKey,
            String objectEtag,
            String storageVersion,
            Instant expiresAt) {
        super(Objects.requireNonNull(id, "id"));
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.fileName = requireText(fileName, "fileName");
        this.mediaType = requireText(mediaType, "mediaType");
        if (contentLength <= 0) {
            throw new IllegalArgumentException("contentLength must be positive");
        }
        this.contentLength = contentLength;
        this.contentSha256 = requireText(contentSha256, "contentSha256");
        this.objectKey = requireText(objectKey, "objectKey");
        this.objectEtag = objectEtag;
        this.storageVersion = storageVersion;
        this.status = AssistantFileStatus.UPLOADED;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.processingGeneration = 1;
    }

    boolean claim(String workerId, Duration lease, Instant now, AssistantFileProcessingProfile requested) {
        if (expiresAt.compareTo(now) <= 0 || status == AssistantFileStatus.DELETING) {
            markExpired(now);
            return false;
        }
        boolean claimable = status == AssistantFileStatus.UPLOADED
                || (status == AssistantFileStatus.PROCESSING
                        && leaseExpiresAt != null
                        && leaseExpiresAt.compareTo(now) <= 0);
        if (!claimable) {
            return false;
        }
        if (requestedProfileSha256 == null) {
            requestedProfileCanonical = requested.canonicalForm();
            requestedProfileSha256 = requested.sha256();
        } else if (!requestedProfileSha256.equals(requested.sha256())
                || !requestedProfileCanonical.equals(requested.canonicalForm())) {
            throw new IllegalStateException("retry requested processing profile changed");
        }
        status = AssistantFileStatus.PROCESSING;
        claimOwner = requireText(workerId, "workerId");
        leaseExpiresAt = now.plus(lease);
        processingAttempt++;
        failureCode = null;
        return true;
    }

    void complete(
            String workerId,
            AssistantFileProcessingProfile resolved,
            UUID embeddingProfileId,
            int embeddingDimensions) {
        requireClaim(workerId);
        bindResolvedProfile(workerId, resolved);
        this.embeddingProfileId = Objects.requireNonNull(embeddingProfileId, "embeddingProfileId");
        this.embeddingDimensions = embeddingDimensions;
        status = AssistantFileStatus.READY;
        claimOwner = null;
        leaseExpiresAt = null;
        failureCode = null;
    }

    void bindResolvedProfile(String workerId, AssistantFileProcessingProfile resolved) {
        requireClaim(workerId);
        if (resolvedProfileSha256 != null
                && (!resolvedProfileSha256.equals(resolved.sha256())
                        || !resolvedProfileCanonical.equals(resolved.canonicalForm()))) {
            throw new IllegalStateException("retry resolved processing profile changed");
        }
        resolvedProfileCanonical = resolved.canonicalForm();
        resolvedProfileSha256 = resolved.sha256();
    }

    void fail(String workerId, String code, boolean retryable, Instant now) {
        requireClaim(workerId);
        failureCode = requireText(code, "code");
        status = retryable && processingAttempt < 3 && expiresAt.isAfter(now)
                ? AssistantFileStatus.UPLOADED
                : AssistantFileStatus.FAILED;
        claimOwner = null;
        leaseExpiresAt = null;
    }

    void markDeleting(Instant now) {
        if (status == AssistantFileStatus.DELETED || status == AssistantFileStatus.EXPIRED) {
            return;
        }
        status = AssistantFileStatus.DELETING;
        deletedAt = now;
        claimOwner = null;
        leaseExpiresAt = null;
    }

    void markExpired(Instant now) {
        if (status == AssistantFileStatus.DELETED) {
            return;
        }
        status = AssistantFileStatus.EXPIRED;
        deletedAt = now;
        claimOwner = null;
        leaseExpiresAt = null;
    }

    void markCleanupComplete(Instant now) {
        if (status == AssistantFileStatus.DELETING) {
            status = AssistantFileStatus.DELETED;
        }
        cleanupCompletedAt = now;
        claimOwner = null;
        leaseExpiresAt = null;
    }

    boolean cleanupComplete() { return cleanupCompletedAt != null; }

    void requireClaim(String workerId) {
        if (status != AssistantFileStatus.PROCESSING || !Objects.equals(claimOwner, workerId)) {
            throw new IllegalStateException("assistant file is not claimed by this worker");
        }
    }

    AssistantFileView view() {
        return new AssistantFileView(
                getId(), fileName, mediaType, contentLength, status, failureCode,
                expiresAt, getCreatedAt());
    }

    UUID organizationId() { return organizationId; }
    UUID actorUserId() { return actorUserId; }
    String fileName() { return fileName; }
    String mediaType() { return mediaType; }
    long contentLength() { return contentLength; }
    String contentSha256() { return contentSha256; }
    String objectKey() { return objectKey; }
    Instant expiresAt() { return expiresAt; }
    AssistantFileStatus status() { return status; }
    long processingGeneration() { return processingGeneration; }
    String requestedProfileCanonical() { return requestedProfileCanonical; }
    String requestedProfileSha256() { return requestedProfileSha256; }
    String resolvedProfileCanonical() { return resolvedProfileCanonical; }
    String resolvedProfileSha256() { return resolvedProfileSha256; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }
}
