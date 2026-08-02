package com.orgmemory.graphrag.postgres;

import static com.orgmemory.graphrag.testkit.ProjectionPermitFixtures.commitPermit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import com.orgmemory.graphrag.testkit.GraphStoreConformance;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ApacheAgeGraphStoreIntegrationTests {

    private static final UUID ORGANIZATION_ID = id("age-store-organization");
    private static final UUID MARKER_ORGANIZATION_ID = id("age-marker-organization");
    private static final UUID ACTOR_ID = id("age-store-actor");
    private static final UUID ASSET_ID = id("age-store-asset");
    private static final UUID REVISION_ID = id("age-store-revision");
    private static final UUID CHUNK_ID = id("age-store-chunk");
    private static final UUID ACL_ID = id("age-store-acl");
    private static final UUID SOURCE_ID = id("age-store-source");
    private static final UUID TARGET_ID = id("age-store-target");
    private static final UUID RELATION_ID = id("age-store-relation");
    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");
    private static final ProjectionNamespace NAMESPACE =
            new ProjectionNamespace(ORGANIZATION_ID, "default", "knowledge");

    private static final DockerImageName IMAGE = DockerImageName.parse(
                    System.getenv().getOrDefault(
                            "ORGMEMORY_POSTGRES_RAG_TEST_IMAGE",
                            "orgmemory/postgres-rag:pg18-age1.8.0-rc0-pgvector0.8.4"))
            .asCompatibleSubstituteFor("postgres");

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(IMAGE);

    private static ApacheAgeGraphStore graph;
    private static PostgresProjectionPublicationStore publications;
    private static JdbcTemplate database;

    @BeforeAll
    static void migrate() {
        DataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        database = new JdbcTemplate(dataSource);
        database.execute("ALTER ROLE "
                + quote(postgres.getUsername())
                + " IN DATABASE "
                + quote(postgres.getDatabaseName())
                + " SET session_preload_libraries = 'age'");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        database.update("""
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, 'AGE selected backend', now(), now(), 0),
                       (?, 'AGE marker failure', now(), now(), 0),
                       (?, 'AGE conformance', now(), now(), 0)
                """,
                ORGANIZATION_ID,
                MARKER_ORGANIZATION_ID,
                GraphStoreConformance.organizationId());
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        DataSourceTransactionManager transactions =
                new DataSourceTransactionManager(dataSource);
        publications = new PostgresProjectionPublicationStore(jdbc, transactions);
        graph = new ApacheAgeGraphStore(jdbc, transactions, publications, 2);
    }

    @Test
    void passesTheSharedGraphStoreContractAsARealBackend() {
        GraphStoreConformance.verify(graph, publications);
    }

    @Test
    void servesOnlyAuthorizedBatchPinnedTopologyAndRetainsHistory() {
        ProjectionBatch first = batch("first", 0);
        graph.stageReplaceRevision(first, revision(first.generation()));
        publications.markPrepared(first, ProjectionKind.GRAPH, NOW);
        ProjectionSnapshot firstSnapshot = publications.publish(
                first, commitPermit(first, NOW), NOW);

        assertEquals(
                List.of(RELATION_ID),
                graph.loadIncidentRelationPage(
                                scope(Set.of(ASSET_ID)),
                                firstSnapshot,
                                Set.of(SOURCE_ID),
                                null,
                                10)
                        .relations()
                        .stream()
                        .map(CanonicalRelation::id)
                        .toList());
        assertTrue(graph.loadIncidentRelationPage(
                        scope(Set.of(id("denied-asset"))),
                        firstSnapshot,
                        Set.of(SOURCE_ID),
                        null,
                        10)
                .relations()
                .isEmpty());
        String edgeProperties = database.queryForObject(
                "SELECT properties::text FROM "
                        + quote("orgmemory_"
                                + ORGANIZATION_ID.toString().replace("-", ""))
                        + "."
                        + quote("DIRECTED")
                        + " WHERE properties::text LIKE ? LIMIT 1",
                String.class,
                "%" + first.id() + "%");
        assertTrue(edgeProperties.contains(ASSET_ID.toString()));
        assertTrue(!edgeProperties.contains("sensitive description"));
        assertTrue(!edgeProperties.contains("sensitive relation description"));

        ProjectionBatch second = batch("second", 1);
        graph.stageDeleteRevision(second, REVISION_ID);
        publications.markPrepared(second, ProjectionKind.GRAPH, NOW.plusSeconds(1));
        ProjectionSnapshot secondSnapshot = publications.publish(
                second,
                commitPermit(second, NOW.plusSeconds(1)),
                NOW.plusSeconds(1));

        assertTrue(graph.loadIncidentRelationPage(
                        scope(Set.of(ASSET_ID)),
                        secondSnapshot,
                        Set.of(SOURCE_ID),
                        null,
                        10)
                .relations()
                .isEmpty());
        assertEquals(
                List.of(RELATION_ID),
                graph.loadIncidentRelationPage(
                                scope(Set.of(ASSET_ID)),
                                firstSnapshot,
                                Set.of(SOURCE_ID),
                                null,
                                10)
                        .relations()
                        .stream()
                        .map(CanonicalRelation::id)
                        .toList());
    }

    @Test
    void rejectsSnapshotsWithoutAnExactReadyMarker() {
        ProjectionNamespace namespace = new ProjectionNamespace(
                MARKER_ORGANIZATION_ID, "marker", "knowledge");
        ProjectionBatch batch = batch(namespace, "missing-marker", 0);
        graph.stageReplaceRevision(
                batch,
                revision(MARKER_ORGANIZATION_ID, batch.generation()));
        publications.markPrepared(batch, ProjectionKind.GRAPH, NOW.plusSeconds(2));
        ProjectionSnapshot snapshot = publications.publish(
                batch,
                commitPermit(batch, NOW.plusSeconds(2)),
                NOW.plusSeconds(2));

        String graphName = "orgmemory_"
                + MARKER_ORGANIZATION_ID.toString().replace("-", "");
        String cypher = "MATCH (marker:batch_marker {batch_id: \""
                + batch.id()
                + "\"}) DELETE marker RETURN count(marker)";
        DataSource corruptionDataSource =
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate corruptionJdbc = new JdbcTemplate(corruptionDataSource);
        new TransactionTemplate(new DataSourceTransactionManager(corruptionDataSource))
                .executeWithoutResult(ignored -> {
                    corruptionJdbc.execute(
                            "SET LOCAL search_path = ag_catalog, \"$user\", public");
                    corruptionJdbc.query(
                            "SELECT * FROM ag_catalog.cypher('"
                                    + graphName
                                    + "'::name, $$"
                                    + cypher
                                    + "$$::cstring) AS (deleted_count ag_catalog.agtype)",
                            resultSet -> {
                                // Execute marker removal to prove reads fail closed.
                            });
                });

        assertThrows(
                IllegalStateException.class,
                () -> graph.validateSnapshot(
                        scope(MARKER_ORGANIZATION_ID, Set.of(ASSET_ID)), snapshot));
    }

    private static ProjectionBatch batch(String key, long previousGeneration) {
        return batch(NAMESPACE, key, previousGeneration);
    }

    private static ProjectionBatch batch(
            ProjectionNamespace namespace,
            String key,
            long previousGeneration) {
        return new ProjectionBatch(
                id("age-store-batch-" + key),
                namespace,
                previousGeneration,
                previousGeneration + 1,
                "age-store-" + key,
                "manifest-age-store-" + key,
                Set.of(ProjectionKind.GRAPH),
                NOW.plusSeconds(previousGeneration));
    }

    private static GraphRevisionContributions revision(long generation) {
        return revision(ORGANIZATION_ID, generation);
    }

    private static GraphRevisionContributions revision(
            UUID organizationId,
            long generation) {
        EvidenceProvenance provenance = new EvidenceProvenance(
                new EvidenceReference(
                        organizationId,
                        ASSET_ID,
                        REVISION_ID,
                        CHUNK_ID,
                        ACL_ID,
                        1),
                generation,
                "test",
                "test-model",
                "test-prompt",
                1,
                NOW);
        return new GraphRevisionContributions(
                organizationId,
                ASSET_ID,
                REVISION_ID,
                generation,
                List.of(
                        new EntityContribution(
                                id("age-source-contribution"),
                                new CanonicalEntity(SOURCE_ID, "OrgMemory"),
                                "PRODUCT",
                                "sensitive description",
                                provenance),
                        new EntityContribution(
                                id("age-target-contribution"),
                                new CanonicalEntity(TARGET_ID, "Secure Search"),
                                "CAPABILITY",
                                "another sensitive description",
                                provenance)),
                List.of(new RelationContribution(
                        id("age-relation-contribution"),
                        new CanonicalRelation(
                                RELATION_ID,
                                SOURCE_ID,
                                TARGET_ID,
                                RelationOrientation.DIRECTED),
                        "BUILDS",
                        List.of("sensitive"),
                        "sensitive relation description",
                        1,
                        provenance)));
    }

    private static AuthorizedEvidenceScope scope(Set<UUID> assetIds) {
        return scope(ORGANIZATION_ID, assetIds);
    }

    private static AuthorizedEvidenceScope scope(
            UUID organizationId,
            Set<UUID> assetIds) {
        return new AuthorizedEvidenceScope(
                organizationId,
                ACTOR_ID,
                null,
                false,
                assetIds,
                "model-v1",
                1,
                NOW);
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
