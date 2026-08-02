package com.orgmemory.worker.authorization;

import com.orgmemory.core.assetregistry.authorization.AssetAuthorizationConvergenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "orgmemory.asset-registry.authorization-convergence",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true)
class AssetAuthorizationConvergenceScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AssetAuthorizationConvergenceScheduler.class);

    private final AssetAuthorizationConvergenceService convergence;
    private final AssetAuthorizationConvergenceProperties properties;

    AssetAuthorizationConvergenceScheduler(
            AssetAuthorizationConvergenceService convergence,
            AssetAuthorizationConvergenceProperties properties) {
        this.convergence = convergence;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString =
                    "${orgmemory.asset-registry.authorization-convergence.poll-interval:1m}")
    void reconcile() {
        var report = convergence.reconcile(properties.batchSize());
        if (report.failed() > 0) {
            LOGGER.warn(
                    "Asset authorization convergence applied {} of {} candidates; {} failed",
                    report.applied(),
                    report.candidates(),
                    report.failed());
        } else if (report.applied() > 0) {
            LOGGER.info(
                    "Asset authorization convergence applied {} candidates",
                    report.applied());
        }
    }
}
