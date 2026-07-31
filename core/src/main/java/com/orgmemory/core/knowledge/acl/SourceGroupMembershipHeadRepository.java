package com.orgmemory.core.knowledge.acl;

import com.orgmemory.core.knowledge.connector.ActiveGroupMembershipRow;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SourceGroupMembershipHeadRepository
        extends JpaRepository<SourceGroupMembershipHead, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SourceGroupMembershipHead> findByOrganizationIdAndGroupPrincipalId(
            UUID organizationId,
            UUID groupPrincipalId);

    @Query("""
            SELECT new com.orgmemory.core.knowledge.connector.ActiveGroupMembershipRow(
                head.groupPrincipalId,
                head.currentSnapshotId,
                head.membershipGeneration,
                seal.sealedAt,
                member.memberPrincipalId)
            FROM SourceGroupMembershipHead head
            JOIN SourceGroupMembershipSnapshotSeal seal
              ON seal.membershipSnapshotId = head.currentSnapshotId
            LEFT JOIN SourceGroupMembershipMember member
              ON member.membershipSnapshotId = head.currentSnapshotId
            WHERE head.organizationId = :organizationId
            """)
    List<ActiveGroupMembershipRow> findActiveMembership(
            @Param("organizationId") UUID organizationId);
}
