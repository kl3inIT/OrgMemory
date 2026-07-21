package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SourceIngestionJobRepository extends JpaRepository<SourceIngestionJob, UUID> {

    @Query(value = """
            SELECT *
            FROM source_ingestion_jobs
            WHERE attempt_count < max_attempts
              AND (
                  (status = 'PENDING' AND available_at <= :now)
                  OR (status = 'PROCESSING' AND lease_until < :now)
              )
            ORDER BY available_at, created_at
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<SourceIngestionJob> lockNextAvailable(@Param("now") Instant now);

    Optional<SourceIngestionJob> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
