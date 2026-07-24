package com.orgmemory.mcp;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orgmemory.mcp")
record McpGatewayProperties(URI apiBaseUrl, Duration requestTimeout) {

    McpGatewayProperties {
        apiBaseUrl = apiBaseUrl == null
                ? URI.create("http://localhost:8080")
                : apiBaseUrl;
        String scheme = apiBaseUrl.getScheme();
        if (!"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(
                    "orgmemory.mcp.api-base-url must use HTTP or HTTPS");
        }
        requestTimeout = requestTimeout == null
                ? Duration.ofSeconds(20)
                : requestTimeout;
        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "orgmemory.mcp.request-timeout must be positive");
        }
    }
}
