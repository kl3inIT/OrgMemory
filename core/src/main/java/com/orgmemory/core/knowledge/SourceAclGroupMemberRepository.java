package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SourceAclGroupMemberRepository extends JpaRepository<SourceAclGroupMember, UUID> {

    List<SourceAclGroupMember> findBySourceAclSnapshotId(UUID sourceAclSnapshotId);

    /**
     * Every sealed membership row in the organization with the generation that sealed
     * it. Callers keep the latest generation per group; joining the seal keeps
     * unsealed, still-mutable snapshots out of the evidence view.
     */
    @Query("""
            SELECT new com.orgmemory.core.knowledge.SealedMembershipRow(
                member.groupPrincipalId,
                member.memberPrincipalId,
                snapshot.id,
                snapshot.aclGeneration,
                seal.sealedAt)
            FROM SourceAclGroupMember member
            JOIN SourceAclSnapshot snapshot ON snapshot.id = member.sourceAclSnapshotId
            JOIN SourceAclSnapshotSeal seal ON seal.sourceAclSnapshotId = member.sourceAclSnapshotId
            WHERE member.organizationId = :organizationId
            """)
    List<SealedMembershipRow> findSealedMembership(@Param("organizationId") UUID organizationId);
}
