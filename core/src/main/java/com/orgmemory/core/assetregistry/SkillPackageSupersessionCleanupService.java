package com.orgmemory.core.assetregistry;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class SkillPackageSupersessionCleanupService {

    private final SkillPackageSupersessionRepository supersessions;
    private final SkillPackageSupersessionCleanupCoordinator coordinator;

    SkillPackageSupersessionCleanupService(
            SkillPackageSupersessionRepository supersessions,
            SkillPackageSupersessionCleanupCoordinator coordinator) {
        this.supersessions = supersessions;
        this.coordinator = coordinator;
    }

    public SkillPackageCleanupOutcome cleanup(UUID supersessionId) {
        return coordinator.cleanup(supersessionId);
    }

    public Map<SkillPackageCleanupOutcome, Integer> cleanupPending(int limit) {
        int boundedLimit = Math.min(Math.max(limit, 1), 100);
        Map<SkillPackageCleanupOutcome, Integer> outcomes =
                new EnumMap<>(SkillPackageCleanupOutcome.class);
        for (UUID id : supersessions.findReadyIds(
                Instant.now(), SkillPackageSupersession.MAX_ATTEMPTS,
                PageRequest.of(0, boundedLimit))) {
            outcomes.merge(coordinator.cleanup(id), 1, Integer::sum);
        }
        return Map.copyOf(outcomes);
    }
}
