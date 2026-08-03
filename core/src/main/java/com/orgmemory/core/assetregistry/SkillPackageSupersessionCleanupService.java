package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.skillcleanup.SkillPackageCleanupOperations;
import com.orgmemory.core.assetregistry.skillcleanup.SkillPackageCleanupSummary;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
class SkillPackageSupersessionCleanupService
        implements SkillPackageCleanupOperations {

    private final SkillPackageSupersessionRepository supersessions;
    private final SkillPackageSupersessionCleanupCoordinator coordinator;

    SkillPackageSupersessionCleanupService(
            SkillPackageSupersessionRepository supersessions,
            SkillPackageSupersessionCleanupCoordinator coordinator) {
        this.supersessions = supersessions;
        this.coordinator = coordinator;
    }

    SkillPackageCleanupOutcome cleanup(UUID supersessionId) {
        return coordinator.cleanup(supersessionId);
    }

    @Override
    public SkillPackageCleanupSummary cleanupPending(int limit) {
        int boundedLimit = Math.min(Math.max(limit, 1), 100);
        Map<SkillPackageCleanupOutcome, Integer> outcomes =
                new EnumMap<>(SkillPackageCleanupOutcome.class);
        for (UUID id : supersessions.findReadyIds(
                Instant.now(), SkillPackageSupersession.MAX_ATTEMPTS,
                PageRequest.of(0, boundedLimit))) {
            outcomes.merge(coordinator.cleanup(id), 1, Integer::sum);
        }
        return new SkillPackageCleanupSummary(
                outcomes.getOrDefault(SkillPackageCleanupOutcome.DELETED, 0),
                outcomes.getOrDefault(
                        SkillPackageCleanupOutcome.RETAINED_BY_IMMUTABLE_REFERENCE,
                        0),
                outcomes.getOrDefault(
                        SkillPackageCleanupOutcome.RETRY_SCHEDULED, 0),
                outcomes.getOrDefault(
                        SkillPackageCleanupOutcome.ALREADY_RESOLVED, 0));
    }
}
