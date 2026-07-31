package com.orgmemory.core.knowledge.graph;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface GraphProcessingProfileRepository
        extends JpaRepository<PersistedGraphProcessingProfile, UUID> {

    Optional<PersistedGraphProcessingProfile> findByCanonicalSha256(String canonicalSha256);
}
