package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable content and policy provenance for one stable Knowledge Asset. */
@Entity
@Table(name = "knowledge_asset_versions")
class KnowledgeAssetVersion extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "knowledge_asset_id", nullable = false, updatable = false)
    private UUID knowledgeAssetId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private long versionNumber;

    @Column(name = "knowledge_space_id", nullable = false, updatable = false)
    private UUID knowledgeSpaceId;

    @Column(name = "source_revision_id", updatable = false)
    private UUID sourceRevisionId;

    @Column(name = "raw_source_object_id", nullable = false, updatable = false)
    private UUID rawSourceObjectId;

    @Column(name = "normalized_record_id", nullable = false, updatable = false)
    private UUID normalizedRecordId;

    @Column(name = "source_acl_snapshot_id", nullable = false, updatable = false)
    private UUID sourceAclSnapshotId;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String content;

    @Column(length = 16, updatable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private KnowledgeClassification classification;

    @Enumerated(EnumType.STRING)
    @Column(name = "declared_access", nullable = false, length = 32, updatable = false)
    private DeclaredAccessScope declaredAccess;

    @Column(name = "content_sha256", nullable = false, length = 64, updatable = false)
    private String contentSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "orgmemory_gate", nullable = false, length = 16, updatable = false)
    private AccessGate orgMemoryGate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeAssetVersionStatus status;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    protected KnowledgeAssetVersion() {
    }

    KnowledgeAssetVersion(
            KnowledgeAsset asset,
            long versionNumber,
            UUID sourceRevisionId,
            NormalizedRecord normalized,
            AccessGate orgMemoryGate) {
        super(UUID.randomUUID());
        if (versionNumber <= 0) {
            throw new IllegalArgumentException("versionNumber must be positive");
        }
        this.organizationId = normalized.getOrganizationId();
        this.knowledgeAssetId = Objects.requireNonNull(asset, "asset").getId();
        this.versionNumber = versionNumber;
        this.knowledgeSpaceId = asset.getKnowledgeSpaceId();
        this.sourceRevisionId = sourceRevisionId;
        this.rawSourceObjectId = normalized.getRawSourceObjectId();
        this.normalizedRecordId = normalized.getId();
        this.sourceAclSnapshotId = normalized.getSourceAclSnapshotId();
        this.departmentId = normalized.getDepartmentId();
        this.title = normalized.getTitle();
        this.content = normalized.getNormalizedContent();
        this.language = normalized.getLanguage();
        this.classification = normalized.getClassification();
        this.declaredAccess = normalized.getDeclaredAccess();
        this.contentSha256 = normalized.getContentSha256();
        this.orgMemoryGate = Objects.requireNonNull(orgMemoryGate, "orgMemoryGate");
        this.status = KnowledgeAssetVersionStatus.PENDING;
    }

    void activate(Instant timestamp) {
        if (status == KnowledgeAssetVersionStatus.ACTIVE) {
            return;
        }
        if (status != KnowledgeAssetVersionStatus.PENDING) {
            throw new IllegalStateException("Only a pending knowledge asset version can be activated");
        }
        status = KnowledgeAssetVersionStatus.ACTIVE;
        activatedAt = Objects.requireNonNull(timestamp, "timestamp");
    }

    void retire(Instant timestamp) {
        if (status != KnowledgeAssetVersionStatus.ACTIVE) {
            throw new IllegalStateException("Only an active knowledge asset version can be retired");
        }
        status = KnowledgeAssetVersionStatus.RETIRED;
        retiredAt = Objects.requireNonNull(timestamp, "timestamp");
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getKnowledgeAssetId() {
        return knowledgeAssetId;
    }

    long getVersionNumber() {
        return versionNumber;
    }

    UUID getKnowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    UUID getSourceRevisionId() {
        return sourceRevisionId;
    }

    UUID getRawSourceObjectId() {
        return rawSourceObjectId;
    }

    UUID getNormalizedRecordId() {
        return normalizedRecordId;
    }

    UUID getSourceAclSnapshotId() {
        return sourceAclSnapshotId;
    }

    KnowledgeAssetVersionStatus getStatus() {
        return status;
    }
}
