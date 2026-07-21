package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EmbeddingProfileRepository extends JpaRepository<EmbeddingProfile, UUID> {

    Optional<EmbeddingProfile> findByOrganizationIdAndProfileKey(UUID organizationId, String profileKey);

    Optional<EmbeddingProfile> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
