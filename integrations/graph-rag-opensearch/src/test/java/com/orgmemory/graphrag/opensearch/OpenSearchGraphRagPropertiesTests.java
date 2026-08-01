package com.orgmemory.graphrag.opensearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OpenSearchGraphRagPropertiesTests {

    @Test
    void validatesOperationalBoundsAndIndexPrefix() {
        OpenSearchGraphRagProperties properties = new OpenSearchGraphRagProperties();
        properties.setEndpoint(URI.create("https://search.example.test:9200"));
        properties.setIndexPrefix("orgmemory-test");
        properties.setConnectTimeout(Duration.ofSeconds(5));
        properties.setSocketTimeout(Duration.ofSeconds(20));
        properties.setBulkMaximumOperations(250);
        properties.setCopyMaximumBytes(1_048_576);
        properties.setGraphMaximumFrontier(500);
        properties.validate();

        assertEquals("orgmemory-test", properties.getIndexPrefix());
        assertEquals(250, properties.getBulkMaximumOperations());
        assertEquals(1_048_576, properties.getCopyMaximumBytes());
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setIndexPrefix("Upper Case"));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setIndexPrefix("a".repeat(181)));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setBulkMaximumOperations(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setCopyMaximumBytes(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setSocketTimeout(Duration.ZERO));
    }

    @Test
    void requiresAValidEndpointAndCompleteBasicCredentials() {
        OpenSearchGraphRagProperties properties = new OpenSearchGraphRagProperties();
        properties.setEndpoint(URI.create("ftp://search.example.test"));
        assertThrows(IllegalArgumentException.class, properties::validate);

        properties.setEndpoint(URI.create("https://search.example.test"));
        properties.setUsername("orgmemory");
        assertThrows(IllegalArgumentException.class, properties::validate);

        properties.setPassword("managed-secret");
        properties.validate();
    }
}
