package com.orgmemory.api.assistant;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orgmemory.assistant")
record AssistantProperties(Duration heartbeatInterval, Duration turnTimeout) {

    AssistantProperties {
        heartbeatInterval = positive(heartbeatInterval, Duration.ofSeconds(15), "heartbeatInterval");
        turnTimeout = positive(turnTimeout, Duration.ofMinutes(2), "turnTimeout");
    }

    private static Duration positive(Duration value, Duration fallback, String field) {
        Duration resolved = value == null ? fallback : value;
        if (resolved.isNegative() || resolved.isZero()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return resolved;
    }
}
