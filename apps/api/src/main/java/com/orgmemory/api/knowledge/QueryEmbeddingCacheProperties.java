package com.orgmemory.api.knowledge;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orgmemory.query-embedding-cache")
record QueryEmbeddingCacheProperties(
        Duration timeToLive,
        Integer maximumEntriesPerNamespace,
        Integer maximumBatchSize,
        Integer cleanupBatchSize,
        Duration cleanupInterval) {

    QueryEmbeddingCacheProperties {
        timeToLive = timeToLive == null ? Duration.ofDays(7) : timeToLive;
        if (timeToLive.compareTo(Duration.ofMillis(1)) < 0
                || timeToLive.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException(
                    "timeToLive must be between 1 millisecond and 30 days");
        }
        maximumEntriesPerNamespace = positiveBounded(
                maximumEntriesPerNamespace,
                10_000,
                1_000_000,
                "maximumEntriesPerNamespace");
        maximumBatchSize = positiveBounded(
                maximumBatchSize,
                64,
                2_048,
                "maximumBatchSize");
        cleanupBatchSize = positiveBounded(
                cleanupBatchSize,
                1_000,
                100_000,
                "cleanupBatchSize");
        cleanupInterval = cleanupInterval == null ? Duration.ofMinutes(15) : cleanupInterval;
        if (cleanupInterval.compareTo(Duration.ofMinutes(1)) < 0
                || cleanupInterval.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException(
                    "cleanupInterval must be between 1 minute and 1 day");
        }
    }

    private static int positiveBounded(
            Integer value,
            int fallback,
            int maximum,
            String field) {
        int resolved = value == null ? fallback : value;
        if (resolved <= 0 || resolved > maximum) {
            throw new IllegalArgumentException(
                    field + " must be between 1 and " + maximum);
        }
        return resolved;
    }
}
