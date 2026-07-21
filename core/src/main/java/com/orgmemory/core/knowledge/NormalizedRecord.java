package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "normalized_records")
public class NormalizedRecord extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "raw_source_object_id", nullable = false, updatable = false)
    private UUID rawSourceObjectId;

    @Column(name = "source_acl_snapshot_id", nullable = false, updatable = false)
    private UUID sourceAclSnapshotId;

    @Column(name = "normalizer_version", nullable = false, length = 64, updatable = false)
    private String normalizerVersion;

    @Column(updatable = false)
    private String title;

    @Column(name = "normalized_content", columnDefinition = "text", updatable = false)
    private String normalizedContent;

    @Column(length = 16, updatable = false)
    private String language;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, updatable = false)
    private KnowledgeClassification classification;

    @Enumerated(EnumType.STRING)
    @Column(name = "declared_access", length = 32, updatable = false)
    private DeclaredAccessScope declaredAccess;

    @Column(name = "content_sha256", length = 64, updatable = false)
    private String contentSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NormalizedRecordStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_code", length = 64, updatable = false)
    private NormalizationIssue issue;

    protected NormalizedRecord() {
    }

    NormalizedRecord(
            RawSourceObject raw,
            SourceAclSnapshot snapshot,
            NormalizeRawSourceCommand command,
            String contentSha256,
            NormalizedRecordStatus status,
            NormalizationIssue issue) {
        super(UUID.randomUUID());
        this.organizationId = raw.getOrganizationId();
        this.rawSourceObjectId = raw.getId();
        this.sourceAclSnapshotId = snapshot.getId();
        this.normalizerVersion = command.normalizerVersion().trim();
        this.title = blankToNull(command.title());
        this.normalizedContent = blankToNull(command.normalizedContent());
        this.language = blankToNull(command.language());
        this.departmentId = raw.getDepartmentId();
        this.classification = raw.getClassification();
        this.declaredAccess = raw.getDeclaredAccess();
        this.contentSha256 = contentSha256;
        this.status = status;
        this.issue = issue;
    }

    void markPromoted() {
        if (status != NormalizedRecordStatus.READY) {
            throw new IllegalStateException("Only a ready normalized record can be promoted");
        }
        status = NormalizedRecordStatus.PROMOTED;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getRawSourceObjectId() {
        return rawSourceObjectId;
    }

    public UUID getSourceAclSnapshotId() {
        return sourceAclSnapshotId;
    }

    public String getNormalizerVersion() {
        return normalizerVersion;
    }

    public String getTitle() {
        return title;
    }

    public String getNormalizedContent() {
        return normalizedContent;
    }

    public String getLanguage() {
        return language;
    }

    public UUID getDepartmentId() {
        return departmentId;
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

    public NormalizedRecordStatus getStatus() {
        return status;
    }

    public NormalizationIssue getIssue() {
        return issue;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
