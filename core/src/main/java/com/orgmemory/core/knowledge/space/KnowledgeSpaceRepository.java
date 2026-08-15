package com.orgmemory.core.knowledge.space;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeSpaceRepository extends JpaRepository<KnowledgeSpace, UUID> {

    Optional<KnowledgeSpace> findByIdAndOrganizationIdAndActiveTrue(UUID id, UUID organizationId);

    boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByIdAndOrganizationIdAndActiveTrue(UUID id, UUID organizationId);

    List<KnowledgeSpace> findByOrganizationIdAndIdInAndActiveTrueOrderByName(
            UUID organizationId,
            Collection<UUID> ids);

    List<KnowledgeSpace> findByOrganizationIdAndIdInOrderByName(
            UUID organizationId,
            Collection<UUID> ids);

    List<KnowledgeSpace> findByOrganizationIdOrderByName(UUID organizationId);

    List<KnowledgeSpace> findByOrganizationIdAndActiveTrueOrderByName(UUID organizationId);

    boolean existsByOrganizationIdAndKey(UUID organizationId, String key);
}
