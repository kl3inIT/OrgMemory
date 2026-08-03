package com.orgmemory.core.assetregistry.workinstruction;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WorkInstructionAcknowledgementRepository
        extends JpaRepository<WorkInstructionAcknowledgement, UUID> {

    Optional<WorkInstructionAcknowledgement>
            findByOrganizationIdAndReleaseIdAndActorUserId(
                    UUID organizationId, UUID releaseId, UUID actorUserId);

    @Modifying
    @Query(value = """
            insert into work_instruction_acknowledgements (
                id, organization_id, asset_id, release_id, release_digest,
                actor_user_id, acknowledged_at, created_at, updated_at, version
            ) values (
                :id, :organizationId, :assetId, :releaseId, :releaseDigest,
                :actorUserId, :timestamp, :timestamp, :timestamp, 0
            )
            on conflict (organization_id, release_id, actor_user_id) do nothing
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
