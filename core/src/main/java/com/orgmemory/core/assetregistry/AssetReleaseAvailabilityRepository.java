package com.orgmemory.core.assetregistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssetReleaseAvailabilityRepository
        extends JpaRepository<AssetReleaseAvailabilityEvent, UUID> {

    Optional<AssetReleaseAvailabilityEvent>
            findFirstByReleaseIdOrderByEffectiveAtDescCreatedAtDesc(UUID releaseId);

    List<AssetReleaseAvailabilityEvent>
            findByReleaseIdOrderByEffectiveAtAscCreatedAtAsc(UUID releaseId);
}
