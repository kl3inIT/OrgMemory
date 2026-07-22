package com.orgmemory.worker.connector;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * Configuration for the staging connector driver. Scheduling is off by default so the
 * connector never runs unless a deployment opts in and points {@code fixtures-directory} at a
 * directory of committed crawl-batch JSON.
 */
@ConfigurationProperties("orgmemory.connector")
public record ConnectorCrawlProperties(
        Boolean schedulingEnabled, Duration pollInterval, String fixturesDirectory) {

    public ConnectorCrawlProperties {
        schedulingEnabled = schedulingEnabled != null && schedulingEnabled;
        pollInterval = pollInterval == null ? Duration.ofSeconds(30) : pollInterval;
        fixturesDirectory = fixturesDirectory == null ? "" : fixturesDirectory.strip();
        Assert.isTrue(!pollInterval.isNegative() && !pollInterval.isZero(), "poll interval must be positive");
    }
}
