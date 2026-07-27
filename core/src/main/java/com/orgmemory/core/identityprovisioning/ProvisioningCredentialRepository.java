package com.orgmemory.core.identityprovisioning;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface ProvisioningCredentialRepository
        extends Repository<ProvisioningCredential, UUID> {

    ProvisioningCredential save(ProvisioningCredential credential);

    Optional<ProvisioningCredential> findByPublicTokenId(String publicTokenId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select credential from ProvisioningCredential credential
            where credential.id = :id
              and credential.organizationId = :organizationId
              and credential.connectionId = :connectionId
            """)
    Optional<ProvisioningCredential> findForUpdate(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId,
            @Param("connectionId") UUID connectionId);

    List<ProvisioningCredential> findByOrganizationIdAndConnectionIdOrderByCreatedAtDesc(
            UUID organizationId, UUID connectionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ProvisioningCredential credential
            set credential.lastUsedAt = :usedAt
            where credential.id = :credentialId
              and credential.organizationId = :organizationId
              and credential.revokedAt is null
            """)
    int markUsed(
            @Param("credentialId") UUID credentialId,
            @Param("organizationId") UUID organizationId,
            @Param("usedAt") Instant usedAt);
}
