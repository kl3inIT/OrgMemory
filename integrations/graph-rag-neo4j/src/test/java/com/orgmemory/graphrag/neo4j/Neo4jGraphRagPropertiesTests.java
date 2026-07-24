package com.orgmemory.graphrag.neo4j;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class Neo4jGraphRagPropertiesTests {

    @Test
    void acceptsAnExplicitProductionConnectionProfile() {
        Neo4jGraphRagProperties properties = new Neo4jGraphRagProperties();
        properties.setUri(URI.create("neo4j+s://graph.example.test:7687"));
        properties.setUsername("orgmemory");
        properties.setPassword("managed-secret");
        properties.setDatabase("orgmemory");

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void failsFastWithoutCredentialsOrWithUnsafeTimeouts() {
        Neo4jGraphRagProperties missingPassword = new Neo4jGraphRagProperties();
        assertThrows(IllegalArgumentException.class, missingPassword::validate);

        Neo4jGraphRagProperties expiredLease = validProperties();
        expiredLease.setCopyLease(Duration.ofSeconds(10));
        expiredLease.setQueryTimeout(Duration.ofSeconds(10));
        assertThrows(IllegalArgumentException.class, expiredLease::validate);
    }

    @Test
    void rejectsUnsupportedUrisAndUnboundedConfigurationValues() {
        Neo4jGraphRagProperties properties = validProperties();
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setMaximumFrontier(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> properties.setDatabase("neo4j; DROP DATABASE neo4j"));

        properties.setUri(URI.create("http://localhost:7474"));
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    private static Neo4jGraphRagProperties validProperties() {
        Neo4jGraphRagProperties properties = new Neo4jGraphRagProperties();
        properties.setPassword("managed-secret");
        return properties;
    }
}
