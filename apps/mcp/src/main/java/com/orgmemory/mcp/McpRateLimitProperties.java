package com.orgmemory.mcp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Bounded, single-node abuse limits for the authenticated MCP surface. */
@ConfigurationProperties("orgmemory.mcp.rate-limit")
record McpRateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("800") long globalCapacity,
        @DefaultValue("1m") Duration globalWindow,
        @DefaultValue("120") long callerCapacity,
        @DefaultValue("1m") Duration callerWindow,
        @DefaultValue("15m") Duration callerExpiry,
        @DefaultValue("10000") long maxTrackedCallers,
        @DefaultValue("262144") long maxBodyBytes) {

    McpRateLimitProperties {
        requirePositive(globalCapacity, "global-capacity");
        requirePositive(callerCapacity, "caller-capacity");
        requirePositive(maxTrackedCallers, "max-tracked-callers");
        requirePositive(maxBodyBytes, "max-body-bytes");
        requirePositive(globalWindow, "global-window");
        requirePositive(callerWindow, "caller-window");
        requirePositive(callerExpiry, "caller-expiry");
        if (callerExpiry.compareTo(callerWindow) < 0) {
            throw new IllegalArgumentException(
                    "orgmemory.mcp.rate-limit.caller-expiry must not be shorter than caller-window");
        }
    }

    private static void requirePositive(long value, String property) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "orgmemory.mcp.rate-limit." + property + " must be positive");
        }
    }

    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    "orgmemory.mcp.rate-limit." + property + " must be positive");
        }
    }
}
