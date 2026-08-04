package com.orgmemory.api.assistant;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orgmemory.assistant")
record AssistantProperties(
        Duration heartbeatInterval,
        Duration turnTimeout,
        RetrievalEngine retrievalEngine,
        Integer retrievalMaximumConcurrency,
        Integer retrievalQueueCapacity,
        Duration retrievalShutdownTimeout) {

    AssistantProperties {
        heartbeatInterval = positive(heartbeatInterval, Duration.ofSeconds(15), "heartbeatInterval");
        turnTimeout = positive(turnTimeout, Duration.ofMinutes(2), "turnTimeout");
        retrievalEngine = retrievalEngine == null ? RetrievalEngine.GRAPH_RAG : retrievalEngine;
        retrievalMaximumConcurrency = positive(
                retrievalMaximumConcurrency, 8, "retrievalMaximumConcurrency");
        retrievalQueueCapacity = positive(
                retrievalQueueCapacity, 32, "retrievalQueueCapacity");
        retrievalShutdownTimeout = positive(
                retrievalShutdownTimeout,
                Duration.ofSeconds(5),
                "retrievalShutdownTimeout");
    }

    private static int positive(Integer value, int fallback, String field) {
        int resolved = value == null ? fallback : value;
        if (resolved < 1) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return resolved;
    }

    private static Duration positive(Duration value, Duration fallback, String field) {
        Duration resolved = value == null ? fallback : value;
        if (resolved.isNegative() || resolved.isZero()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return resolved;
    }

    enum RetrievalEngine {
        GRAPH_RAG,
        CANONICAL_HYBRID
    }
}
