package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface KnowledgeAssetRepository extends JpaRepository<KnowledgeAsset, UUID> {

    Optional<KnowledgeAsset> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<KnowledgeAsset> findByOrganizationIdAndSourceObjectId(
            UUID organizationId, UUID sourceObjectId);
}
