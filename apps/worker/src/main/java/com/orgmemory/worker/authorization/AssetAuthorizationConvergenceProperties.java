package com.orgmemory.worker.authorization;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("orgmemory.asset-registry.authorization-convergence")
public record AssetAuthorizationConvergenceProperties(Integer batchSize) {

    public AssetAuthorizationConvergenceProperties {
        batchSize = batchSize == null ? 50 : batchSize;
        Assert.isTrue(
                batchSize > 0 && batchSize <= 100,
                "Asset authorization convergence batch size must be between 1 and 100");
    }
}
