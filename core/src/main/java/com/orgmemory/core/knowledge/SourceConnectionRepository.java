package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourceConnectionRepository extends JpaRepository<SourceConnection, UUID> {

    List<SourceConnection> findByOrganizationId(UUID organizationId);

    Optional<SourceConnection> findByOrganizationIdAndSourceSystemAndSourceConnectionKey(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey);
}
