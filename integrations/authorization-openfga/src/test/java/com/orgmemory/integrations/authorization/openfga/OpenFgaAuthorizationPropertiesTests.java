package com.orgmemory.integrations.authorization.openfga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OpenFgaAuthorizationPropertiesTests {

    @Test
    void suppliesBoundedRetryDefaults() {
        var properties = new OpenFgaAuthorizationProperties(
                URI.create("http://localhost:8081"),
                "store-id",
                "model-id",
                null,
                null,
                null);

        assertEquals(3, properties.maxRetries());
        assertEquals(Duration.ofMillis(100), properties.minimumRetryDelay());
        assertEquals(Duration.ofSeconds(5), properties.requestTimeout());
    }

    @Test
    void rejectsInvalidExternalConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OpenFgaAuthorizationProperties(
                        URI.create("http://localhost:8081"),
                        "store-id",
                        "model-id",
                        -1,
                        Duration.ofMillis(100),
                        Duration.ofMillis(100)));
    }
}
