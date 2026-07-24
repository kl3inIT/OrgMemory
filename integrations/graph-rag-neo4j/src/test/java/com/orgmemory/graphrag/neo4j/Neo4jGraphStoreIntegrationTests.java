package com.orgmemory.graphrag.neo4j;

import com.orgmemory.graphrag.testkit.GraphStoreConformance;
import com.orgmemory.graphrag.testkit.InMemoryProjectionPublicationStore;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;

@Testcontainers
class Neo4jGraphStoreIntegrationTests {

    private static final String PASSWORD = "orgmemory-test-password";

    @Container
    static final Neo4jContainer neo4j =
            new Neo4jContainer("neo4j:5.26-community")
                    .withAdminPassword(PASSWORD);

    @Test
    void passesTheSharedGraphStoreSecurityAndLifecycleContract() {
        Neo4jGraphRagProperties properties = properties();
        Neo4jGraphRagAutoConfiguration configuration =
                new Neo4jGraphRagAutoConfiguration();
        try (Driver driver = configuration.neo4jGraphRagDriver(properties)) {
            Neo4jOperations operations =
                    configuration.neo4jGraphRagOperations(driver, properties);
            var publications = new InMemoryProjectionPublicationStore();
            var store = configuration.neo4jGraphStore(
                    operations, publications, properties);

            GraphStoreConformance.verify(store, publications);
        }
    }

    private static Neo4jGraphRagProperties properties() {
        Neo4jGraphRagProperties properties = new Neo4jGraphRagProperties();
        properties.setUri(URI.create(neo4j.getBoltUrl()));
        properties.setPassword(PASSWORD);
        properties.setWriteBatchSize(2);
        properties.setCopyPageSize(2);
        properties.setMaximumFrontier(100);
        properties.setCopyWaitTimeout(Duration.ofMinutes(2));
        properties.setCopyLease(Duration.ofMinutes(1));
        return properties;
    }
}
