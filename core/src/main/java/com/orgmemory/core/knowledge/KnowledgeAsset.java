package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.permission.KnowledgeResource;
import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_assets")
public class KnowledgeAsset extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

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
    private KnowledgeAssetStatus status;

    @Column(name = "activated_at", nullable = false, updatable = false)
    private Instant activatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    protected KnowledgeAsset() {
    }

    KnowledgeAsset(NormalizedRecord normalized, AccessGate orgMemoryGate, Instant activatedAt) {
        super(UUID.randomUUID());
        this.organizationId = normalized.getOrganizationId();
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
        this.orgMemoryGate = orgMemoryGate;
        this.status = KnowledgeAssetStatus.ACTIVE;
        this.activatedAt = activatedAt;
    }

    void retire(Instant retiredAt) {
        if (status != KnowledgeAssetStatus.ACTIVE) {
            throw new IllegalStateException("Knowledge asset is already retired");
        }
        status = KnowledgeAssetStatus.RETIRED;
        this.retiredAt = retiredAt;
    }

    KnowledgeResource toPermissionResource() {
        return new KnowledgeResource(
                organizationId.toString(),
                getId().toString(),
                departmentId == null ? null : departmentId.toString(),
                classification,
                declaredAccess);
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getRawSourceObjectId() {
        return rawSourceObjectId;
    }

    public UUID getNormalizedRecordId() {
        return normalizedRecordId;
    }

    public UUID getSourceAclSnapshotId() {
        return sourceAclSnapshotId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getLanguage() {
        return language;
    }

    public KnowledgeClassification getClassification() {
        return classification;
    }

    public DeclaredAccessScope getDeclaredAccess() {
        return declaredAccess;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public AccessGate getOrgMemoryGate() {
        return orgMemoryGate;
    }

    public KnowledgeAssetStatus getStatus() {
        return status;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }
}
