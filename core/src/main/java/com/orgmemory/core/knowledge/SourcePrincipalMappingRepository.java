package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourcePrincipalMappingRepository extends JpaRepository<SourcePrincipalMapping, UUID> {

    List<SourcePrincipalMapping> findByOrganizationIdAndStatus(
            UUID organizationId,
            SourcePrincipalMappingStatus status);

    Optional<SourcePrincipalMapping> findBySourcePrincipalIdAndStatus(
            UUID sourcePrincipalId,
            SourcePrincipalMappingStatus status);

    Optional<SourcePrincipalMapping> findByOrganizationIdAndSourcePrincipalIdAndStatus(
            UUID organizationId,
            UUID sourcePrincipalId,
            SourcePrincipalMappingStatus status);
}
