package com.orgmemory.core.knowledge.acl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One immutable typed member belonging to a source group membership snapshot. */
@Entity
@Table(name = "source_group_membership_members")
public class SourceGroupMembershipMember {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "membership_snapshot_id", nullable = false, updatable = false)
    private UUID membershipSnapshotId;

    @Column(name = "member_principal_id", nullable = false, updatable = false)
    private UUID memberPrincipalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_principal_kind", nullable = false, length = 16, updatable = false)
    private SourcePrincipalKind memberPrincipalKind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SourceGroupMembershipMember() {
    }

    SourceGroupMembershipMember(
            UUID organizationId,
            UUID membershipSnapshotId,
            UUID memberPrincipalId,
            SourcePrincipalKind memberPrincipalKind,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.membershipSnapshotId = membershipSnapshotId;
        this.memberPrincipalId = memberPrincipalId;
        this.memberPrincipalKind = memberPrincipalKind;
        this.createdAt = createdAt;
    }

    UUID getMemberPrincipalId() {
        return memberPrincipalId;
    }

    SourcePrincipalKind getMemberPrincipalKind() {
        return memberPrincipalKind;
    }
}
