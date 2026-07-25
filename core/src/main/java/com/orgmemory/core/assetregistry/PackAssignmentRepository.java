package com.orgmemory.core.assetregistry;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PackAssignmentRepository extends JpaRepository<PackAssignment, UUID> {

    Optional<PackAssignment> findByOrganizationIdAndPackReleaseIdAndActorUserId(
            UUID organizationId, UUID packReleaseId, UUID actorUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment
            from PackAssignment assignment
            where assignment.id = :id
              and assignment.organizationId = :organizationId
              and assignment.actorUserId = :actorUserId
            """)
    Optional<PackAssignment> findForUpdateByIdAndOrganizationIdAndActorUserId(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId,
            @Param("actorUserId") UUID actorUserId);

    @Modifying
    @Query(value = """
            insert into pack_assignments (
                id, organization_id, pack_asset_id, pack_release_id,
                release_digest, actor_user_id, status, started_at,
                created_at, updated_at, version
            ) values (
                :id, :organizationId, :assetId, :releaseId,
                :releaseDigest, :actorUserId, 'IN_PROGRESS', :timestamp,
                :timestamp, :timestamp, 0
            )
            on conflict (organization_id, pack_release_id, actor_user_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId,
            @Param("assetId") UUID assetId,
            @Param("releaseId") UUID releaseId,
            @Param("releaseDigest") String releaseDigest,
            @Param("actorUserId") UUID actorUserId,
            @Param("timestamp") Instant timestamp);
}
