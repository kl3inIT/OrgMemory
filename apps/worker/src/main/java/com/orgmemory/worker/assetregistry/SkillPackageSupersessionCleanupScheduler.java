package com.orgmemory.worker.assetregistry;

import com.orgmemory.core.assetregistry.SkillPackageCleanupOutcome;
import com.orgmemory.core.assetregistry.SkillPackageSupersessionCleanupService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SkillPackageSupersessionCleanupScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SkillPackageSupersessionCleanupScheduler.class);

    private final SkillPackageSupersessionCleanupService cleanup;

    SkillPackageSupersessionCleanupScheduler(
            SkillPackageSupersessionCleanupService cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(
            fixedDelayString =
                    "${orgmemory.asset-registry.skill-package-cleanup-interval:1m}")
    void cleanup() {
        Map<SkillPackageCleanupOutcome, Integer> outcomes = cleanup.cleanupPending(25);
        if (!outcomes.isEmpty()) {
            LOGGER.info("Skill package supersession cleanup outcomes={}", outcomes);
        }
    }
}
