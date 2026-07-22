package com.orgmemory.integrations.authorization.openfga;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("orgmemory.authorization.openfga")
public record OpenFgaAuthorizationProperties(
        URI apiUrl,
        String storeId,
        String authorizationModelId,
        Integer maxRetries,
        Duration minimumRetryDelay,
        Duration requestTimeout) {

    public OpenFgaAuthorizationProperties {
        Assert.notNull(apiUrl, "orgmemory.authorization.openfga.api-url is required");
        Assert.hasText(storeId, "orgmemory.authorization.openfga.store-id is required");
        Assert.hasText(
                authorizationModelId,
                "orgmemory.authorization.openfga.authorization-model-id is required");
        maxRetries = maxRetries == null ? 3 : maxRetries;
        minimumRetryDelay = minimumRetryDelay == null ? Duration.ofMillis(100) : minimumRetryDelay;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(5) : requestTimeout;
        Assert.isTrue(maxRetries >= 0, "orgmemory.authorization.openfga.max-retries cannot be negative");
        Assert.isTrue(
                !minimumRetryDelay.isNegative(),
                "orgmemory.authorization.openfga.minimum-retry-delay cannot be negative");
        Assert.isTrue(
                !requestTimeout.isNegative() && !requestTimeout.isZero(),
                "orgmemory.authorization.openfga.request-timeout must be positive");
    }
}
