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
@Table(name = "source_acl_entries")
public class SourceAclEntry {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "source_acl_snapshot_id", nullable = false, updatable = false)
    private UUID sourceAclSnapshotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, length = 32, updatable = false)
    private SourcePrincipalType principalType;

    @Column(name = "principal_key", nullable = false, length = 512, updatable = false)
    private String principalKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private AccessGate gate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SourceAclEntry() {
    }

    SourceAclEntry(
            UUID organizationId,
            UUID sourceAclSnapshotId,
            SourceAclEntryCommand command,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.sourceAclSnapshotId = sourceAclSnapshotId;
        this.principalType = command.principalType();
        this.principalKey = command.principalKey().trim();
        this.gate = command.gate();
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceAclSnapshotId() {
        return sourceAclSnapshotId;
    }

    public SourcePrincipalType getPrincipalType() {
        return principalType;
    }

    public String getPrincipalKey() {
        return principalKey;
    }

    public AccessGate getGate() {
        return gate;
    }
}
