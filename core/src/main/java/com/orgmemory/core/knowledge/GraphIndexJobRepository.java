package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GraphIndexJobRepository extends JpaRepository<GraphIndexJob, UUID> {

    Optional<GraphIndexJob> findByKnowledgeAssetVersionId(UUID knowledgeAssetVersionId);

    Optional<GraphIndexJob> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query(value = """
            SELECT *
            FROM graph_index_jobs
            WHERE (
                status = 'PENDING'
                AND attempt_count < max_attempts
                AND available_at <= :now
            )
               OR (
                status = 'PROCESSING'
                AND lease_until < :now
            )
            ORDER BY available_at, created_at
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<GraphIndexJob> lockNextAvailable(@Param("now") Instant now);
}
