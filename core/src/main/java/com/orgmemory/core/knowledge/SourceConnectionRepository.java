package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourceConnectionRepository extends JpaRepository<SourceConnection, UUID> {

    List<SourceConnection> findByOrganizationId(UUID organizationId);

    List<SourceConnection> findByOrganizationIdAndSourceSystemOrderBySourceConnectionKeyAsc(
            UUID organizationId, String sourceSystem);

    Optional<SourceConnection> findByOrganizationIdAndSourceSystemAndSourceConnectionKey(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey);

    /** Across tenants: a worker crawls for every organization that enabled the connection. */
    List<SourceConnection> findBySourceSystemAndCrawlEnabledTrue(String sourceSystem);
}
