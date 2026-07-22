package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourcePrincipalRepository extends JpaRepository<SourcePrincipal, UUID> {

    Optional<SourcePrincipal> findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalKey(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            String externalKey);
}
