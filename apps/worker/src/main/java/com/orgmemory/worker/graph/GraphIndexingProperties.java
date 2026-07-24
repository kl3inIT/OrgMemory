package com.orgmemory.worker.graph;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("orgmemory.graph-rag.indexing")
public record GraphIndexingProperties(
        Boolean schedulingEnabled,
        Duration pollInterval,
        String workerId,
        Duration leaseDuration,
        Duration extractionTimeout,
        Integer maximumConcurrency) {

    public GraphIndexingProperties {
        schedulingEnabled = schedulingEnabled == null || schedulingEnabled;
        pollInterval = pollInterval == null ? Duration.ofSeconds(3) : pollInterval;
        workerId = workerId == null || workerId.isBlank()
                ? "graph-worker-" + UUID.randomUUID()
                : workerId.strip();
        leaseDuration = leaseDuration == null ? Duration.ofMinutes(10) : leaseDuration;
        extractionTimeout =
                extractionTimeout == null ? Duration.ofMinutes(2) : extractionTimeout;
        maximumConcurrency = maximumConcurrency == null ? 4 : maximumConcurrency;
        Assert.isTrue(
                !pollInterval.isNegative() && !pollInterval.isZero(),
                "graph indexing poll interval must be positive");
        Assert.isTrue(
                !leaseDuration.isNegative() && !leaseDuration.isZero(),
                "graph indexing lease duration must be positive");
        Assert.isTrue(
                !extractionTimeout.isNegative() && !extractionTimeout.isZero(),
                "graph extraction timeout must be positive");
        Assert.isTrue(
                maximumConcurrency > 0 && maximumConcurrency <= 32,
                "graph extraction concurrency must be between 1 and 32");
    }
}
