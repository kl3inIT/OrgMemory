package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_revisions")
class SourceRevision extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "knowledge_space_id", nullable = false, updatable = false)
    private UUID knowledgeSpaceId;

    @Column(name = "source_object_id", nullable = false, updatable = false)
    private UUID sourceObjectId;

    @Column(name = "evidence_blob_id", nullable = false, updatable = false)
    private UUID evidenceBlobId;

    @Column(name = "revision_number", nullable = false, updatable = false)
    private long revisionNumber;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "media_type", nullable = false, updatable = false)
    private String mediaType;

    @Column(name = "content_length", nullable = false, updatable = false)
    private long contentLength;

    @Column(name = "content_sha256", nullable = false, length = 64, updatable = false)
    private String contentSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private KnowledgeClassification classification;

    @Enumerated(EnumType.STRING)
    @Column(name = "declared_access", nullable = false, length = 32, updatable = false)
    private DeclaredAccessScope declaredAccess;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SourceRevisionStatus status;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", length = 512)
    private String failureMessage;

    @Column(name = "pipeline_version", length = 64)
    private String pipelineVersion;

    @Column(name = "parser_version", length = 64)
    private String parserVersion;

    @Column(name = "chunker_version", length = 64)
    private String chunkerVersion;

    @Column(name = "embedding_profile_id")
    private UUID embeddingProfileId;

    @Column(name = "embedding_dimensions")
    private Integer embeddingDimensions;

    @Column(name = "raw_source_object_id")
    private UUID rawSourceObjectId;

    @Column(name = "normalized_record_id")
    private UUID normalizedRecordId;

    @Column(name = "knowledge_asset_id")
    private UUID knowledgeAssetId;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected SourceRevision() {
    }

    /**
     * @param revisionNumber the object's next revision ordinal. An upload object only ever has
     *     one; a connector object gains another every time the source edits its content, and the
     *     unique constraint on (object, number) is what keeps two crawls from claiming the same
     *     ordinal.
     */
    SourceRevision(
            UUID id,
            SourceObject source,
            EvidenceBlob blob,
            String fileName,
            long revisionNumber) {
        super(id);
        if (revisionNumber < 1) {
            throw new IllegalArgumentException("revision number starts at 1");
        }
        this.organizationId = source.getOrganizationId();
        this.knowledgeSpaceId = source.getKnowledgeSpaceId();
        this.sourceObjectId = source.getId();
        this.evidenceBlobId = blob.getId();
        this.revisionNumber = revisionNumber;
        this.fileName = fileName;
        this.mediaType = blob.getMediaType();
        this.contentLength = blob.getContentLength();
        this.contentSha256 = blob.getContentSha256();
        this.classification = source.getClassification();
        this.declaredAccess = source.getDeclaredAccess();
        this.departmentId = source.getDepartmentId();
        this.createdByUserId = source.getCreatedByUserId();
        this.status = SourceRevisionStatus.RECEIVED;
    }

    void transitionTo(SourceRevisionStatus next) {
        this.status = next;
        this.failureCode = null;
        this.failureMessage = null;
    }

    void quarantine(String code, String message) {
        this.status = SourceRevisionStatus.QUARANTINED;
        this.failureCode = code;
        this.failureMessage = SourceFailureMessage.truncate(message);
    }

    void fail(String code, String message) {
        this.status = SourceRevisionStatus.FAILED;
        this.failureCode = code;
        this.failureMessage = SourceFailureMessage.truncate(message);
    }

    void waitForRetry(String code, String message) {
        this.status = SourceRevisionStatus.RECEIVED;
        this.failureCode = code;
        this.failureMessage = SourceFailureMessage.truncate(message);
    }

    void ready(
            String pipelineVersion,
            String parserVersion,
            String chunkerVersion,
            EmbeddingProfileRef embeddingProfile,
            RawSourceRef raw,
            NormalizedRecordRef normalized,
            KnowledgeAssetRef asset,
            Instant processedAt) {
        this.status = SourceRevisionStatus.READY;
        this.failureCode = null;
        this.failureMessage = null;
        this.pipelineVersion = pipelineVersion;
        this.parserVersion = parserVersion;
        this.chunkerVersion = chunkerVersion;
        this.embeddingProfileId = embeddingProfile.id();
        this.embeddingDimensions = embeddingProfile.dimensions();
        this.rawSourceObjectId = raw.rawSourceObjectId();
        this.normalizedRecordId = normalized.normalizedRecordId();
        this.knowledgeAssetId = asset.knowledgeAssetId();
        this.processedAt = processedAt;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getKnowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    UUID getSourceObjectId() {
        return sourceObjectId;
    }

    UUID getEvidenceBlobId() {
        return evidenceBlobId;
    }

    String getFileName() {
        return fileName;
    }

    String getMediaType() {
        return mediaType;
    }

    long getContentLength() {
        return contentLength;
    }

    String getContentSha256() {
        return contentSha256;
    }

    KnowledgeClassification getClassification() {
        return classification;
    }

    DeclaredAccessScope getDeclaredAccess() {
        return declaredAccess;
    }

    UUID getDepartmentId() {
        return departmentId;
    }

    UUID getCreatedByUserId() {
        return createdByUserId;
    }

    SourceRevisionStatus getStatus() {
        return status;
    }

    String getFailureCode() {
        return failureCode;
    }

    String getFailureMessage() {
        return failureMessage;
    }

    UUID getEmbeddingProfileId() {
        return embeddingProfileId;
    }

    Integer getEmbeddingDimensions() {
        return embeddingDimensions;
    }
}
