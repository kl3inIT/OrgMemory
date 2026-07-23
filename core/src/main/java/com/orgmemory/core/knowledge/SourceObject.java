package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "source_objects")
class SourceObject extends BaseEntity {

    /** Uploads are not a connector, so the ledger names their system itself. */
    static final String NATIVE_UPLOAD_SYSTEM = "upload";

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "knowledge_space_id", nullable = false, updatable = false)
    private UUID knowledgeSpaceId;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "acl_authority", nullable = false, length = 32, updatable = false)
    private AclAuthority aclAuthority;

    @Column(name = "source_system", nullable = false, length = 64, updatable = false)
    private String sourceSystem;

    @Column(name = "source_connection_key", nullable = false, length = 128, updatable = false)
    private String sourceConnectionKey;

    @Column(name = "external_object_id", nullable = false, length = 512, updatable = false)
    private String externalObjectId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeClassification classification;

    @Enumerated(EnumType.STRING)
    @Column(name = "declared_access", nullable = false, length = 32)
    private DeclaredAccessScope declaredAccess;

    @Column(name = "current_revision_id")
    private UUID currentRevisionId;

    @Column(name = "latest_revision_id")
    private UUID latestRevisionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SourceObjectStatus status;

    protected SourceObject() {
    }

    SourceObject(
            UUID id,
            UUID organizationId,
            UUID knowledgeSpaceId,
            UUID departmentId,
            UUID createdByUserId,
            String title,
            KnowledgeClassification classification,
            DeclaredAccessScope declaredAccess) {
        super(id);
        this.organizationId = organizationId;
        this.knowledgeSpaceId = knowledgeSpaceId;
        this.departmentId = departmentId;
        this.createdByUserId = createdByUserId;
        this.aclAuthority = AclAuthority.ORGMEMORY;
        this.sourceSystem = NATIVE_UPLOAD_SYSTEM;
        this.sourceConnectionKey = "manual-upload";
        this.externalObjectId = id.toString();
        this.title = title;
        this.classification = classification;
        this.declaredAccess = declaredAccess;
        this.status = SourceObjectStatus.ACTIVE;
    }

    private SourceObject(
            UUID id,
            UUID organizationId,
            UUID knowledgeSpaceId,
            UUID departmentId,
            UUID createdByUserId,
            AclAuthority aclAuthority,
            String sourceSystem,
            String sourceConnectionKey,
            String externalObjectId,
            String title,
            KnowledgeClassification classification,
            DeclaredAccessScope declaredAccess) {
        super(id);
        this.organizationId = organizationId;
        this.knowledgeSpaceId = knowledgeSpaceId;
        this.departmentId = departmentId;
        this.createdByUserId = createdByUserId;
        this.aclAuthority = aclAuthority;
        this.sourceSystem = sourceSystem;
        this.sourceConnectionKey = sourceConnectionKey;
        this.externalObjectId = externalObjectId;
        this.title = title;
        this.classification = classification;
        this.declaredAccess = declaredAccess;
        this.status = SourceObjectStatus.ACTIVE;
    }

    /** Creates an active connector-owned source object keyed by its external identity. */
    static SourceObject connectorObject(
            UUID id,
            UUID organizationId,
            UUID knowledgeSpaceId,
            UUID departmentId,
            UUID createdByUserId,
            AclAuthority aclAuthority,
            String sourceSystem,
            String sourceConnectionKey,
            String externalObjectId,
            String title,
            KnowledgeClassification classification,
            DeclaredAccessScope declaredAccess) {
        return new SourceObject(
                id,
                organizationId,
                knowledgeSpaceId,
                departmentId,
                createdByUserId,
                aclAuthority,
                sourceSystem,
                sourceConnectionKey,
                externalObjectId,
                title,
                classification,
                declaredAccess);
    }

    void stageRevision(UUID revisionId) {
        if (status != SourceObjectStatus.ACTIVE) {
            throw new IllegalStateException("An archived source object cannot accept a revision");
        }
        latestRevisionId = revisionId;
    }

    void publishRevision(UUID revisionId) {
        if (status != SourceObjectStatus.ACTIVE) {
            throw new IllegalStateException("An archived source object cannot publish a revision");
        }
        if (!Objects.equals(latestRevisionId, revisionId)) {
            throw new IllegalStateException("Only the latest source revision can be published");
        }
        this.currentRevisionId = revisionId;
    }

    /** Retires this object from retrieval; its evidence and history are retained. */
    void archive() {
        this.status = SourceObjectStatus.ARCHIVED;
    }

    SourceObjectStatus getStatus() {
        return status;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getKnowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    UUID getDepartmentId() {
        return departmentId;
    }

    UUID getCreatedByUserId() {
        return createdByUserId;
    }

    AclAuthority getAclAuthority() {
        return aclAuthority;
    }

    String getSourceSystem() {
        return sourceSystem;
    }

    String getSourceConnectionKey() {
        return sourceConnectionKey;
    }

    String getExternalObjectId() {
        return externalObjectId;
    }

    String getTitle() {
        return title;
    }

    KnowledgeClassification getClassification() {
        return classification;
    }

    DeclaredAccessScope getDeclaredAccess() {
        return declaredAccess;
    }

    UUID getCurrentRevisionId() {
        return currentRevisionId;
    }

    UUID getLatestRevisionId() {
        return latestRevisionId;
    }
}
