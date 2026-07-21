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
@Table(name = "raw_source_objects")
public class RawSourceObject extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Column(name = "source_system", nullable = false, length = 64, updatable = false)
    private String sourceSystem;

    @Column(name = "source_connection_key", nullable = false, length = 128, updatable = false)
    private String sourceConnectionKey;

    @Column(name = "external_object_id", nullable = false, length = 512, updatable = false)
    private String externalObjectId;

    @Column(name = "source_version", nullable = false, updatable = false)
    private String sourceVersion;

    @Column(name = "object_type", nullable = false, length = 64, updatable = false)
    private String objectType;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(name = "raw_content", nullable = false, columnDefinition = "text", updatable = false)
    private String rawContent;

    @Column(name = "source_uri", length = 2048, updatable = false)
    private String sourceUri;

    @Column(name = "payload_sha256", nullable = false, length = 64, updatable = false)
    private String payloadSha256;

    @Column(name = "source_modified_at", updatable = false)
    private Instant sourceModifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, updatable = false)
    private KnowledgeClassification classification;

    @Enumerated(EnumType.STRING)
    @Column(name = "declared_access", length = 32, updatable = false)
    private DeclaredAccessScope declaredAccess;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RawSourceStatus status;

    protected RawSourceObject() {
    }

    RawSourceObject(
            RegisterRawSourceCommand command,
            String payloadSha256,
            String canonicalSourceUri,
            Instant sourceModifiedAt) {
        super(UUID.randomUUID());
        this.organizationId = command.organizationId();
        this.departmentId = command.departmentId();
        this.sourceSystem = command.sourceSystem().trim();
        this.sourceConnectionKey = command.sourceConnectionKey().trim();
        this.externalObjectId = command.externalObjectId().trim();
        this.sourceVersion = command.sourceVersion().trim();
        this.objectType = command.objectType().trim();
        this.title = command.title().trim();
        this.rawContent = command.rawContent();
        this.sourceUri = canonicalSourceUri;
        this.payloadSha256 = payloadSha256;
        this.sourceModifiedAt = sourceModifiedAt;
        this.classification = command.classification();
        this.declaredAccess = command.declaredAccess();
        this.status = RawSourceStatus.RECEIVED;
    }

    void markNormalized() {
        if (status == RawSourceStatus.RECEIVED) {
            status = RawSourceStatus.NORMALIZED;
        }
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getSourceConnectionKey() {
        return sourceConnectionKey;
    }

    public String getExternalObjectId() {
        return externalObjectId;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public String getObjectType() {
        return objectType;
    }

    public String getTitle() {
        return title;
    }

    String getRawContent() {
        return rawContent;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public String getPayloadSha256() {
        return payloadSha256;
    }

    public Instant getSourceModifiedAt() {
        return sourceModifiedAt;
    }

    public KnowledgeClassification getClassification() {
        return classification;
    }

    public DeclaredAccessScope getDeclaredAccess() {
        return declaredAccess;
    }

    public RawSourceStatus getStatus() {
        return status;
    }
}
