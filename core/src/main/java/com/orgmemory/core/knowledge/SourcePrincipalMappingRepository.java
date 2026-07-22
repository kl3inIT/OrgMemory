package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourcePrincipalMappingRepository extends JpaRepository<SourcePrincipalMapping, UUID> {

    Optional<SourcePrincipalMapping> findBySourcePrincipalIdAndStatus(
            UUID sourcePrincipalId,
            SourcePrincipalMappingStatus status);

    Optional<SourcePrincipalMapping> findByOrganizationIdAndSourcePrincipalIdAndStatus(
            UUID organizationId,
            UUID sourcePrincipalId,
            SourcePrincipalMappingStatus status);
}
