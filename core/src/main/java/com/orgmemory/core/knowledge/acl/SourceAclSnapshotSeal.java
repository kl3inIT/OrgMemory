package com.orgmemory.core.knowledge.acl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_acl_snapshot_seals")
public class SourceAclSnapshotSeal {

    @Id
    @Column(name = "source_acl_snapshot_id", nullable = false, updatable = false)
    private UUID sourceAclSnapshotId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "entry_count", nullable = false, updatable = false)
    private int entryCount;

    @Column(name = "entries_sha256", nullable = false, length = 64, updatable = false)
    private String entriesSha256;

    @Column(name = "sealed_at", nullable = false, updatable = false)
    private Instant sealedAt;

    protected SourceAclSnapshotSeal() {
    }

    public SourceAclSnapshotSeal(
            UUID sourceAclSnapshotId,
            UUID organizationId,
            int entryCount,
            String entriesSha256,
            Instant sealedAt) {
        this.sourceAclSnapshotId = sourceAclSnapshotId;
        this.organizationId = organizationId;
        this.entryCount = entryCount;
        this.entriesSha256 = entriesSha256;
        this.sealedAt = sealedAt;
    }
}
