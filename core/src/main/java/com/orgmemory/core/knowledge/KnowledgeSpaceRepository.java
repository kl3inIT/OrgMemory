package com.orgmemory.core.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface KnowledgeSpaceRepository extends JpaRepository<KnowledgeSpace, UUID> {

    Optional<KnowledgeSpace> findByIdAndOrganizationIdAndActiveTrue(UUID id, UUID organizationId);

    boolean existsByIdAndOrganizationIdAndActiveTrue(UUID id, UUID organizationId);

    List<KnowledgeSpace> findByOrganizationIdAndIdInAndActiveTrueOrderByName(
            UUID organizationId,
            Collection<UUID> ids);
}
