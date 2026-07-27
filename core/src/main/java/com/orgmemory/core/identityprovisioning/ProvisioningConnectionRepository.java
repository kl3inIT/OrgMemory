package com.orgmemory.core.identityprovisioning;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface ProvisioningConnectionRepository
        extends Repository<ProvisioningConnection, UUID> {

    ProvisioningConnection save(ProvisioningConnection connection);

    Optional<ProvisioningConnection> findByIdAndOrganizationId(
            UUID id, UUID organizationId);

    List<ProvisioningConnection> findByOrganizationIdOrderByAlias(UUID organizationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE provisioning_connections
            SET operational_state = :nextState,
                enabled_at = CASE
                    WHEN :nextState = 'ENABLED' THEN COALESCE(enabled_at, :changedAt)
                    ELSE enabled_at
                END,
                updated_at = :changedAt,
                version = version + 1
            WHERE id = :connectionId
              AND organization_id = :organizationId
              AND operational_state = :expectedState
              AND version = :expectedVersion
            """, nativeQuery = true)
    int compareAndSetOperationalState(
            @Param("organizationId") UUID organizationId,
            @Param("connectionId") UUID connectionId,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedState") String expectedState,
            @Param("nextState") String nextState,
            @Param("changedAt") Instant changedAt);
}
