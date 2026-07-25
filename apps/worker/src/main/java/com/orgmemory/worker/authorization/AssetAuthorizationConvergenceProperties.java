package com.orgmemory.worker.authorization;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("orgmemory.asset-registry.authorization-convergence")
public record AssetAuthorizationConvergenceProperties(
        Boolean schedulingEnabled,
        Duration pollInterval,
        Integer batchSize) {

    public AssetAuthorizationConvergenceProperties {
        schedulingEnabled = schedulingEnabled == null || schedulingEnabled;
        pollInterval = pollInterval == null ? Duration.ofMinutes(1) : pollInterval;
        batchSize = batchSize == null ? 50 : batchSize;
        Assert.isTrue(
                !pollInterval.isNegative() && !pollInterval.isZero(),
                "Asset authorization convergence poll interval must be positive");
        Assert.isTrue(
                batchSize > 0 && batchSize <= 100,
                "Asset authorization convergence batch size must be between 1 and 100");
    }
}
