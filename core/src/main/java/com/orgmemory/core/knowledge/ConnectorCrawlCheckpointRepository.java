package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConnectorCrawlCheckpointRepository extends JpaRepository<ConnectorCrawlCheckpoint, UUID> {

    Optional<ConnectorCrawlCheckpoint> findByOrganizationIdAndSourceSystemAndSourceConnectionKey(
            UUID organizationId, String sourceSystem, String sourceConnectionKey);
}
