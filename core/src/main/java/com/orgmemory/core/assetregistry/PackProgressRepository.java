package com.orgmemory.core.assetregistry;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PackProgressRepository extends JpaRepository<PackProgress, UUID> {

    Optional<PackProgress> findByOrganizationIdAndAssignmentIdAndItemKey(
            UUID organizationId, UUID assignmentId, String itemKey);

    List<PackProgress> findByOrganizationIdAndAssignmentIdOrderByItemKey(
            UUID organizationId, UUID assignmentId);

    @Modifying
    @Query(value = """
            insert into pack_progress (
                id, organization_id, assignment_id, item_key, completed,
                completed_at, created_at, updated_at, version
            ) values (
                :id, :organizationId, :assignmentId, :itemKey, :completed,
                case
                    when :completed
                    then cast(:timestamp as timestamp with time zone)
                    else cast(null as timestamp with time zone)
                end,
                :timestamp, :timestamp, 0
            )
            on conflict (organization_id, assignment_id, item_key)
            do update set
                completed = excluded.completed,
                completed_at = excluded.completed_at,
                updated_at = excluded.updated_at,
                version = pack_progress.version + 1
            """, nativeQuery = true)
    int upsert(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId,
            @Param("assignmentId") UUID assignmentId,
            @Param("itemKey") String itemKey,
            @Param("completed") boolean completed,
            @Param("timestamp") Instant timestamp);
}
