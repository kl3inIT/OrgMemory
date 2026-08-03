package com.orgmemory.worker.assetregistry;

import com.orgmemory.core.assetregistry.skillcleanup.SkillPackageCleanupOperations;
import com.orgmemory.core.assetregistry.skillcleanup.SkillPackageCleanupSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SkillPackageSupersessionCleanupScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SkillPackageSupersessionCleanupScheduler.class);

    private final SkillPackageCleanupOperations cleanup;

    SkillPackageSupersessionCleanupScheduler(
            SkillPackageCleanupOperations cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(
            fixedDelayString =
                    "${orgmemory.asset-registry.skill-package-cleanup-interval:1m}")
    void cleanup() {
        SkillPackageCleanupSummary summary = cleanup.cleanupPending(25);
        if (!summary.isEmpty()) {
            LOGGER.info("Skill package supersession cleanup summary={}", summary);
        }
    }
}
