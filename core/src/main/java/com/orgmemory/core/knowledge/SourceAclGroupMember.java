package com.orgmemory.core.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The membership of a {@code SOURCE_GROUP} principal as sealed with one ACL snapshot
 * generation. Rows are append-only immutable evidence, guarded by the same seal and
 * append-only triggers as {@link SourceAclEntry}.
 */
@Entity
@Table(name = "source_acl_group_members")
public class SourceAclGroupMember {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "source_acl_snapshot_id", nullable = false, updatable = false)
    private UUID sourceAclSnapshotId;

    @Column(name = "group_principal_id", nullable = false, updatable = false)
    private UUID groupPrincipalId;

    @Column(name = "member_principal_id", nullable = false, updatable = false)
    private UUID memberPrincipalId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SourceAclGroupMember() {
    }

    SourceAclGroupMember(
            UUID organizationId,
            UUID sourceAclSnapshotId,
            UUID groupPrincipalId,
            UUID memberPrincipalId,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.sourceAclSnapshotId = sourceAclSnapshotId;
        this.groupPrincipalId = groupPrincipalId;
        this.memberPrincipalId = memberPrincipalId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    UUID getSourceAclSnapshotId() {
        return sourceAclSnapshotId;
    }

    UUID getGroupPrincipalId() {
        return groupPrincipalId;
    }

    UUID getMemberPrincipalId() {
        return memberPrincipalId;
    }
}
