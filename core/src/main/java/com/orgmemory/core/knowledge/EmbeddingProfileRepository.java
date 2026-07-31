package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmbeddingProfileRepository extends JpaRepository<EmbeddingProfile, UUID> {

    Optional<EmbeddingProfile> findByOrganizationIdAndProfileKey(UUID organizationId, String profileKey);

    Optional<EmbeddingProfile> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
