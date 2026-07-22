package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface KnowledgeAssetRepository extends JpaRepository<KnowledgeAsset, UUID> {

    Optional<KnowledgeAsset> findByNormalizedRecordId(UUID normalizedRecordId);

    Optional<KnowledgeAsset> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
