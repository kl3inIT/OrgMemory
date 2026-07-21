package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "source_acl_heads")
class SourceAclHead extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "source_system", nullable = false, length = 64, updatable = false)
    private String sourceSystem;

    @Column(name = "source_connection_key", nullable = false, length = 128, updatable = false)
    private String sourceConnectionKey;

    @Column(name = "external_object_id", nullable = false, length = 512, updatable = false)
    private String externalObjectId;

    @Column(name = "current_raw_source_object_id", nullable = false)
    private UUID currentRawSourceObjectId;

    @Column(name = "current_snapshot_id", nullable = false)
    private UUID currentSnapshotId;

    @Column(name = "acl_generation", nullable = false)
    private long aclGeneration;

    protected SourceAclHead() {
    }

    SourceAclHead(RawSourceObject raw, SourceAclSnapshot snapshot) {
        super(UUID.randomUUID());
        this.organizationId = raw.getOrganizationId();
        this.sourceSystem = raw.getSourceSystem();
        this.sourceConnectionKey = raw.getSourceConnectionKey();
        this.externalObjectId = raw.getExternalObjectId();
        this.currentRawSourceObjectId = raw.getId();
        this.currentSnapshotId = snapshot.getId();
        this.aclGeneration = snapshot.getAclGeneration();
    }

    void advance(RawSourceObject raw, SourceAclSnapshot snapshot) {
        if (!organizationId.equals(raw.getOrganizationId())
                || !sourceSystem.equals(raw.getSourceSystem())
                || !sourceConnectionKey.equals(raw.getSourceConnectionKey())
                || !externalObjectId.equals(raw.getExternalObjectId())) {
            throw new IllegalArgumentException("ACL head identity does not match the raw source object");
        }
        if (snapshot.getAclGeneration() <= aclGeneration) {
            throw new KnowledgeIngestionConflictException("ACL generation must advance monotonically");
        }
        currentRawSourceObjectId = raw.getId();
        currentSnapshotId = snapshot.getId();
        aclGeneration = snapshot.getAclGeneration();
    }

    UUID getCurrentRawSourceObjectId() {
        return currentRawSourceObjectId;
    }

    UUID getCurrentSnapshotId() {
        return currentSnapshotId;
    }

    long getAclGeneration() {
        return aclGeneration;
    }
}
