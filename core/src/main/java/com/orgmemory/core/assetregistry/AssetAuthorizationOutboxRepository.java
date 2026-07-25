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
              and outbox.status = :status
            order by outbox.createdAt
            """)
    List<AssetAuthorizationOutbox> findForAsset(
            @Param("assetId") UUID assetId,
            @Param("status") AssetAuthorizationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select outbox
            from AssetAuthorizationOutbox outbox
            where outbox.status = com.orgmemory.core.assetregistry.AssetAuthorizationStatus.PENDING
            order by outbox.createdAt
            """)
    List<AssetAuthorizationOutbox> findPending(Pageable pageable);

    @Query("""
            select count(outbox)
            from AssetAuthorizationOutbox outbox
            where outbox.assetId = :assetId
              and outbox.status = com.orgmemory.core.assetregistry.AssetAuthorizationStatus.PENDING
            """)
    long countPending(@Param("assetId") UUID assetId);

    List<AssetAuthorizationOutbox> findByIdIn(Collection<UUID> ids);
}
