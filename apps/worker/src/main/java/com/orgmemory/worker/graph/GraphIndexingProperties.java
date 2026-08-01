package com.orgmemory.worker.graph;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("orgmemory.graph-rag.indexing")
public record GraphIndexingProperties(
        String workerId,
        Duration leaseDuration,
        Duration extractionTimeout,
        Integer maximumConcurrency,
        Integer maxJobsPerInvocation,
        Duration maxWallClock) {

    public GraphIndexingProperties {
        workerId = workerId == null || workerId.isBlank()
                ? "graph-worker-" + UUID.randomUUID()
                : workerId.strip();
        leaseDuration = leaseDuration == null ? Duration.ofMinutes(10) : leaseDuration;
        extractionTimeout =
                extractionTimeout == null ? Duration.ofMinutes(2) : extractionTimeout;
        maximumConcurrency = maximumConcurrency == null ? 4 : maximumConcurrency;
        maxJobsPerInvocation = maxJobsPerInvocation == null ? 5 : maxJobsPerInvocation;
        maxWallClock = maxWallClock == null ? Duration.ofSeconds(30) : maxWallClock;
        Assert.isTrue(
                !leaseDuration.isNegative() && !leaseDuration.isZero(),
                "graph indexing lease duration must be positive");
        Assert.isTrue(
                !extractionTimeout.isNegative() && !extractionTimeout.isZero(),
                "graph extraction timeout must be positive");
        Assert.isTrue(
                maximumConcurrency > 0 && maximumConcurrency <= 32,
                "graph extraction concurrency must be between 1 and 32");
        Assert.isTrue(
                maxJobsPerInvocation > 0 && maxJobsPerInvocation <= 100,
                "graph maxJobsPerInvocation must be between 1 and 100");
        Assert.isTrue(
                !maxWallClock.isNegative()
                        && !maxWallClock.isZero()
                        && maxWallClock.compareTo(Duration.ofMinutes(10)) <= 0,
                "graph maxWallClock must be between zero and ten minutes");
    }
}
