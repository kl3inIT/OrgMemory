package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.connector.ConnectorSyncComponent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConnectorCrawlCheckpointRepository extends JpaRepository<ConnectorCrawlCheckpoint, UUID> {

    Optional<ConnectorCrawlCheckpoint>
            findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndComponent(
                    UUID organizationId,
                    String sourceSystem,
                    String sourceConnectionKey,
                    ConnectorSyncComponent component);

    List<ConnectorCrawlCheckpoint>
            findByOrganizationIdAndSourceSystemAndSourceConnectionKeyOrderByComponent(
                    UUID organizationId, String sourceSystem, String sourceConnectionKey);
}
