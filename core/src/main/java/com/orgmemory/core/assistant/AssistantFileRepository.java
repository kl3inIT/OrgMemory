package com.orgmemory.core.assistant;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AssistantFileRepository extends JpaRepository<AssistantFile, UUID> {

    Optional<AssistantFile> findByIdAndOrganizationIdAndActorUserId(
            UUID id, UUID organizationId, UUID actorUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from AssistantFile f where f.id = :id")
    Optional<AssistantFile> findForUpdate(@Param("id") UUID id);

    List<AssistantFile> findAllByIdInAndOrganizationIdAndActorUserId(
            Collection<UUID> ids, UUID organizationId, UUID actorUserId);

    @Query("""
            select f from AssistantFile f
            where f.organizationId = :organizationId
              and f.actorUserId = :actorUserId
              and f.status not in (com.orgmemory.core.assistant.AssistantFileStatus.DELETED,
                                   com.orgmemory.core.assistant.AssistantFileStatus.EXPIRED)
              and f.expiresAt > :now
            order by f.createdAt desc, f.id desc
            """)
    Page<AssistantFile> recent(
            @Param("organizationId") UUID organizationId,
            @Param("actorUserId") UUID actorUserId,
            @Param("now") Instant now,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select f from AssistantFile f
            where f.status = com.orgmemory.core.assistant.AssistantFileStatus.UPLOADED
               or (f.status = com.orgmemory.core.assistant.AssistantFileStatus.PROCESSING
                   and f.leaseExpiresAt <= :now)
            order by f.createdAt, f.id
            """)
    List<AssistantFile> claimable(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select f from AssistantFile f
            where (f.status in (com.orgmemory.core.assistant.AssistantFileStatus.DELETING,
                                com.orgmemory.core.assistant.AssistantFileStatus.EXPIRED)
                   and f.cleanupCompletedAt is null)
               or (f.status not in (com.orgmemory.core.assistant.AssistantFileStatus.DELETING,
                                    com.orgmemory.core.assistant.AssistantFileStatus.DELETED,
                                    com.orgmemory.core.assistant.AssistantFileStatus.EXPIRED)
                   and f.expiresAt <= :now)
            order by f.expiresAt, f.id
            """)
    List<AssistantFile> cleanupCandidate(@Param("now") Instant now, Pageable pageable);
}
