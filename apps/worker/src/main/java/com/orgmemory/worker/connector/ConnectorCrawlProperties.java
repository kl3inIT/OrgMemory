package com.orgmemory.worker.connector;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the staging connector driver. Scheduling is off by default so the
 * connector never runs unless a deployment opts in and points {@code fixtures-directory} at a
 * directory of committed crawl-batch JSON.
 */
@ConfigurationProperties("orgmemory.connector")
public record ConnectorCrawlProperties(String fixturesDirectory) {

    public ConnectorCrawlProperties {
        fixturesDirectory = fixturesDirectory == null ? "" : fixturesDirectory.strip();
    }
}
