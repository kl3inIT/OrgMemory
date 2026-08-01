package com.orgmemory.core.knowledge.acl;

import com.orgmemory.core.shared.BaseEntity;
import com.orgmemory.core.shared.error.BusinessConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "source_acl_heads")
public class SourceAclHead extends BaseEntity {

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

    public SourceAclHead(SourceAclTarget target, SourceAclSnapshot snapshot) {
        super(UUID.randomUUID());
        requireMatchingSnapshot(target, snapshot);
        this.organizationId = target.organizationId();
        this.sourceSystem = target.sourceSystem();
        this.sourceConnectionKey = target.sourceConnectionKey();
        this.externalObjectId = target.externalObjectId();
        this.currentRawSourceObjectId = target.rawSourceObjectId();
        this.currentSnapshotId = snapshot.getId();
        this.aclGeneration = snapshot.getAclGeneration();
    }

    public void advance(SourceAclTarget target, SourceAclSnapshot snapshot) {
        requireMatchingSnapshot(target, snapshot);
        if (!organizationId.equals(target.organizationId())
                || !sourceSystem.equals(target.sourceSystem())
                || !sourceConnectionKey.equals(target.sourceConnectionKey())
                || !externalObjectId.equals(target.externalObjectId())) {
            throw new IllegalArgumentException("ACL head identity does not match the raw source object");
        }
        if (snapshot.getAclGeneration() <= aclGeneration) {
            throw new BusinessConflictException(
                    "knowledge-ingestion.conflict",
                    "ACL generation must advance monotonically");
        }
        currentRawSourceObjectId = target.rawSourceObjectId();
        currentSnapshotId = snapshot.getId();
        aclGeneration = snapshot.getAclGeneration();
    }

    private static void requireMatchingSnapshot(
            SourceAclTarget target,
            SourceAclSnapshot snapshot) {
        if (!target.organizationId().equals(snapshot.getOrganizationId())
                || !target.rawSourceObjectId().equals(snapshot.getRawSourceObjectId())) {
            throw new IllegalArgumentException(
                    "ACL target and snapshot must identify the same source");
        }
    }

    public UUID getCurrentRawSourceObjectId() {
        return currentRawSourceObjectId;
    }

    public UUID getCurrentSnapshotId() {
        return currentSnapshotId;
    }

    public long getAclGeneration() {
        return aclGeneration;
    }
}
