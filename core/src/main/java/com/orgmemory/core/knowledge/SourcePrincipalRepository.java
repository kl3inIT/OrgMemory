package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourcePrincipalRepository extends JpaRepository<SourcePrincipal, UUID> {

    Optional<SourcePrincipal> findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalKey(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            String externalKey);

    Optional<SourcePrincipal> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<SourcePrincipal> findByOrganizationId(UUID organizationId);
}
