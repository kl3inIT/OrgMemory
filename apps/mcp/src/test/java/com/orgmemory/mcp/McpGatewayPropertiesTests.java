package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class McpGatewayPropertiesTests {

    @Test
    void defaultsKeepConnectionFailureFastAndAllowGraphRetrievalToFinish() {
        var properties = properties(null, null);

        assertEquals(
                Duration.ofSeconds(5),
                properties.connectTimeout());
        assertEquals(
                Duration.ofSeconds(75),
                properties.requestTimeout());
    }

    @Test
    void rejectsNonPositiveTransportTimeouts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(Duration.ZERO, Duration.ofSeconds(75)));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(
                        Duration.ofSeconds(-1),
                        Duration.ofSeconds(75)));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(Duration.ofSeconds(5), Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(-1)));
    }

    private static McpGatewayProperties properties(
            Duration connectTimeout,
            Duration requestTimeout) {
        return new McpGatewayProperties(
                null,
                connectTimeout,
                requestTimeout,
                null,
                null,
                null,
                null,
                null);
    }
}
