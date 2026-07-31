package com.orgmemory.core.knowledge.acl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Digest proving the complete immutable member set of one membership snapshot. */
@Entity
@Table(name = "source_group_membership_snapshot_seals")
public class SourceGroupMembershipSnapshotSeal {

    @Id
    @Column(name = "membership_snapshot_id", nullable = false, updatable = false)
    private UUID membershipSnapshotId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "group_principal_id", nullable = false, updatable = false)
    private UUID groupPrincipalId;

    @Column(name = "membership_generation", nullable = false, updatable = false)
    private long membershipGeneration;

    @Column(name = "member_count", nullable = false, updatable = false)
    private int memberCount;

    @Column(name = "members_sha256", nullable = false, length = 64, updatable = false)
    private String membersSha256;

    @Column(name = "sealed_at", nullable = false, updatable = false)
    private Instant sealedAt;

    protected SourceGroupMembershipSnapshotSeal() {
    }

    SourceGroupMembershipSnapshotSeal(
            SourceGroupMembershipSnapshot snapshot,
            int memberCount,
            String membersSha256,
            Instant sealedAt) {
        this.membershipSnapshotId = snapshot.getId();
        this.organizationId = snapshot.getOrganizationId();
        this.groupPrincipalId = snapshot.getGroupPrincipalId();
        this.membershipGeneration = snapshot.getMembershipGeneration();
        this.memberCount = memberCount;
        this.membersSha256 = membersSha256;
        this.sealedAt = sealedAt;
    }

    String getMembersSha256() {
        return membersSha256;
    }

    Instant getSealedAt() {
        return sealedAt;
    }
}
