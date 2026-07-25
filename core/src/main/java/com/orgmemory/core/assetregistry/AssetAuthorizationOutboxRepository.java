package com.orgmemory.core.assetregistry;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AssetAuthorizationOutboxRepository
        extends JpaRepository<AssetAuthorizationOutbox, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select outbox
            from AssetAuthorizationOutbox outbox
            where outbox.assetId = :assetId
              and (
                    (outbox.status = com.orgmemory.core.assetregistry.AssetAuthorizationStatus.PENDING
                     and outbox.nextAttemptAt <= :now)
                    or
                    (outbox.status = com.orgmemory.core.assetregistry.AssetAuthorizationStatus.IN_FLIGHT
                     and outbox.leaseUntil <= :now)
              )
            order by outbox.createdAt
            """)
    List<AssetAuthorizationOutbox> findClaimableForAsset(
            @Param("assetId") UUID assetId,
            @Param("now") java.time.Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select outbox
            from AssetAuthorizationOutbox outbox
            where (
                    outbox.status = com.orgmemory.core.assetregistry.AssetAuthorizationStatus.PENDING
                    and outbox.nextAttemptAt <= :now
                  )
               or (
                    outbox.status = com.orgmemory.core.assetregistry.AssetAuthorizationStatus.IN_FLIGHT
                    and outbox.leaseUntil <= :now
                  )
            order by outbox.createdAt
            """)
    List<AssetAuthorizationOutbox> findClaimable(
            @Param("now") java.time.Instant now,
            Pageable pageable);

    @Query("""
            select count(outbox)
            from AssetAuthorizationOutbox outbox
            where outbox.assetId = :assetId
              and outbox.status <> com.orgmemory.core.assetregistry.AssetAuthorizationStatus.APPLIED
            """)
    long countUnresolved(@Param("assetId") UUID assetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AssetAuthorizationOutbox> findByIdIn(Collection<UUID> ids);
}
