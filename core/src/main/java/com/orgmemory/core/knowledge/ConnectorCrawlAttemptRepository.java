package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConnectorCrawlAttemptRepository extends JpaRepository<ConnectorCrawlAttempt, UUID> {

    List<ConnectorCrawlAttempt>
            findByOrganizationIdAndSourceSystemAndSourceConnectionKeyOrderByAttemptedAtDesc(
                    UUID organizationId, String sourceSystem, String sourceConnectionKey, Limit limit);
}
