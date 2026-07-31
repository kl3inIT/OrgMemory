package com.orgmemory.core.assetregistry;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SkillPackageSupersessionRepository
        extends JpaRepository<SkillPackageSupersession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from SkillPackageSupersession item where item.id = :id")
    Optional<SkillPackageSupersession> findForUpdate(@Param("id") UUID id);

    @Query("""
            select item.id
            from SkillPackageSupersession item
            where item.nextAttemptAt <= :now
              and item.attemptCount < :maximumAttempts
            order by item.nextAttemptAt, item.createdAt
            """)
    List<UUID> findReadyIds(
            @Param("now") Instant now,
            @Param("maximumAttempts") int maximumAttempts,
            Pageable pageable);
}
