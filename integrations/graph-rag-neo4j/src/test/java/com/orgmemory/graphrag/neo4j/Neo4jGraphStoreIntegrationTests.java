package com.orgmemory.graphrag.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.testkit.GraphStoreConformance;
import com.orgmemory.graphrag.testkit.InMemoryProjectionPublicationStore;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    @Test
    void copyForwardPagesByConsumedSourceRows() {
        Neo4jGraphRagProperties properties = properties();
        Neo4jGraphRagAutoConfiguration configuration =
                new Neo4jGraphRagAutoConfiguration();
        try (Driver driver = configuration.neo4jGraphRagDriver(properties)) {
            Neo4jOperations operations =
                    configuration.neo4jGraphRagOperations(driver, properties);
            var publications = new InMemoryProjectionPublicationStore();
            GraphStore store = configuration.neo4jGraphStore(
                    operations, publications, properties);
            ProjectionNamespace namespace =
                    new ProjectionNamespace(UUID.randomUUID(), "default", "knowledge");
            ProjectionBatch first = batch(namespace, 0, "copy-source");
            UUID firstEntityId = UUID.randomUUID();
            UUID secondEntityId = UUID.randomUUID();

            seedCopySourcePage(
                    operations,
                    first,
                    UUID.randomUUID(),
                    firstEntityId,
                    secondEntityId);
            publications.markPrepared(first, ProjectionKind.GRAPH, Instant.now());
            publications.publish(first, Instant.now());

            ProjectionBatch second = batch(namespace, 1, "copy-target");
            store.stageDeleteRevision(second, UUID.randomUUID());

            long linkedContributions = operations.read(transaction -> transaction.run(
                            """
                            MATCH (contribution:OrgMemoryGraphEntityContribution {
                                batchId: $batchId
                            })-[:CONTRIBUTES_TO]->()
                            RETURN count(contribution) AS linked
                            """,
                            Map.of("batchId", second.id().toString()))
                    .single()
                    .get("linked")
                    .asLong());
            assertEquals(
                    2L,
                    linkedContributions,
                    "a filtered source row must not hide later copy-forward pages");
        }
    }

    private static ProjectionBatch batch(
            ProjectionNamespace namespace,
            long expectedPreviousGeneration,
            String identity) {
        return new ProjectionBatch(
                UUID.randomUUID(),
                namespace,
                expectedPreviousGeneration,
                expectedPreviousGeneration + 1,
                identity,
                identity + "-manifest",
                Set.of(ProjectionKind.GRAPH),
                Instant.now());
    }

    private static void seedCopySourcePage(
            Neo4jOperations operations,
            ProjectionBatch batch,
            UUID missingEntityId,
            UUID firstEntityId,
            UUID secondEntityId) {
        List<Map<String, Object>> entities = List.of(
                entity(batch, firstEntityId),
                entity(batch, secondEntityId));
        List<Map<String, Object>> contributions = List.of(
                contribution(batch, "001", UUID.randomUUID(), missingEntityId),
                contribution(batch, "002", UUID.randomUUID(), firstEntityId),
                contribution(batch, "003", UUID.randomUUID(), secondEntityId));
        operations.writeWithoutResult(transaction -> {
            transaction.run(
                            """
                            UNWIND $rows AS row
                            CREATE (:OrgMemoryGraphEntity {
                                key: row.key,
                                batchId: row.batchId,
                                generation: row.generation,
                                entityId: row.entityId
                            })
                            """,
                            Map.of("rows", entities))
                    .consume();
            transaction.run(
                            """
                            UNWIND $rows AS row
                            CREATE (:OrgMemoryGraphEntityContribution {
                                key: row.key,
                                batchId: row.batchId,
                                generation: row.generation,
                                contributionId: row.contributionId,
                                entityId: row.entityId,
                                sourceRevisionId: row.sourceRevisionId
                            })
                            """,
                            Map.of("rows", contributions))
                    .consume();
        });
    }

    private static Map<String, Object> entity(
            ProjectionBatch batch,
            UUID entityId) {
        return Map.of(
                "key", batch.id() + ":" + entityId,
                "batchId", batch.id().toString(),
                "generation", batch.generation(),
                "entityId", entityId.toString());
    }

    private static Map<String, Object> contribution(
            ProjectionBatch batch,
            String order,
            UUID contributionId,
            UUID entityId) {
        return Map.of(
                "key", batch.id() + ":" + order,
                "batchId", batch.id().toString(),
                "generation", batch.generation(),
                "contributionId", contributionId.toString(),
                "entityId", entityId.toString(),
                "sourceRevisionId", UUID.randomUUID().toString());
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
