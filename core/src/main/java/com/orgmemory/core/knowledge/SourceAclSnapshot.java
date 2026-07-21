package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.AccessGate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_acl_snapshots")
public class SourceAclSnapshot {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "raw_source_object_id", nullable = false, updatable = false)
    private UUID rawSourceObjectId;

    @Column(name = "acl_generation", nullable = false, updatable = false)
    private long aclGeneration;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_status", nullable = false, length = 32, updatable = false)
    private AclCaptureStatus captureStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_gate", nullable = false, length = 16, updatable = false)
    private AccessGate defaultGate;

    @Column(name = "acl_sha256", length = 64, updatable = false)
    private String aclSha256;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @Column(name = "valid_until", updatable = false)
    private Instant validUntil;

    protected SourceAclSnapshot() {
    }

    SourceAclSnapshot(
            UUID organizationId,
            UUID rawSourceObjectId,
            long aclGeneration,
            AclCaptureStatus captureStatus,
            AccessGate defaultGate,
            String aclSha256,
            Instant capturedAt,
            Instant validUntil) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.rawSourceObjectId = rawSourceObjectId;
        this.aclGeneration = aclGeneration;
        this.captureStatus = captureStatus;
        this.defaultGate = defaultGate;
        this.aclSha256 = aclSha256;
        this.capturedAt = capturedAt;
        this.validUntil = validUntil;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getRawSourceObjectId() {
        return rawSourceObjectId;
    }

    public long getAclGeneration() {
        return aclGeneration;
    }

    public AclCaptureStatus getCaptureStatus() {
        return captureStatus;
    }

    public AccessGate getDefaultGate() {
        return defaultGate;
    }

    public String getAclSha256() {
        return aclSha256;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public boolean isUsableAt(Instant instant) {
        return captureStatus == AclCaptureStatus.COMPLETE
                && validUntil != null
                && validUntil.isAfter(instant);
    }

    public boolean isComplete() {
        return captureStatus == AclCaptureStatus.COMPLETE;
    }
}
