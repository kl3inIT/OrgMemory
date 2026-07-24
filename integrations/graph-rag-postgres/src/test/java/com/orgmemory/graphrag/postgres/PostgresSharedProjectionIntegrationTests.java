package com.orgmemory.graphrag.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.LexicalIndex;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore.PublicationConflictException;
import com.orgmemory.graphrag.storage.VectorIndex;
import com.orgmemory.graphrag.testkit.ProjectionPublicationConformance;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresSharedProjectionIntegrationTests {

    private static final UUID CONFORMANCE_ORGANIZATION_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZATION_ID = id("shared-snapshot-organization");
    private static final UUID ACTOR_ID = id("shared-snapshot-actor");
    private static final UUID ASSET_ID = id("shared-snapshot-asset");
    private static final UUID REVISION_ID = id("shared-snapshot-revision");
    private static final UUID CHUNK_ID = id("shared-snapshot-chunk");
    private static final UUID ACL_ID = id("shared-snapshot-acl");
    private static final UUID PROFILE_ID = id("shared-snapshot-profile");
    private static final UUID ENTITY_A_ID = id("shared-snapshot-entity-a");
    private static final UUID ENTITY_B_ID = id("shared-snapshot-entity-b");
    private static final UUID RELATION_ID = id("shared-snapshot-relation");
    private static final Instant NOW = Instant.parse("2026-07-24T06:00:00Z");
    private static final ProjectionNamespace NAMESPACE =
            new ProjectionNamespace(ORGANIZATION_ID, "default", "knowledge");

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static PostgresProjectionPublicationStore publications;
    private static JdbcTemplate plainJdbc;
    private static PostgresContentStore content;
    private static PostgresLexicalIndex lexical;
    private static PostgresVectorIndex vectors;
    private static PostgresGraphStore graph;

    @BeforeAll
    static void migrate() {
        DataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        plainJdbc = new JdbcTemplate(dataSource);
        plainJdbc.update(
                """
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, 'Projection conformance', now(), now(), 0),
                       (?, 'Shared snapshot', now(), now(), 0)
                """,
                CONFORMANCE_ORGANIZATION_ID,
                ORGANIZATION_ID);
        NamedParameterJdbcTemplate jdbc =
                new NamedParameterJdbcTemplate(dataSource);
        DataSourceTransactionManager transactions =
                new DataSourceTransactionManager(dataSource);
        publications = new PostgresProjectionPublicationStore(jdbc, transactions);
        content = new PostgresContentStore(jdbc, transactions, publications);
        lexical = new PostgresLexicalIndex(jdbc, transactions, publications);
        vectors = new PostgresVectorIndex(jdbc, transactions, publications);
        graph = new PostgresGraphStore(jdbc, transactions, publications);
    }

    @Test
    void publicationStorePassesSharedConformance() {
        ProjectionPublicationConformance.verify(() -> publications);
    }

    @Test
    void allAdaptersReadOneExactAuthorizedSnapshotAndRetainHistory() {
        ProjectionBatch first = batch("first", 0);
        EvidenceReference evidence = evidence();
        content.stageUpsert(
                first,
                List.of(new ContentStore.ContentRecord(
                        CHUNK_ID.toString(),
                        evidence,
                        ContentStore.ContentKind.CHUNK,
                        "Probation policy is sixty days",
                        5,
                        Map.of("title", "Employee handbook"))));
        lexical.stageUpsert(
                first,
                List.of(new LexicalIndex.LexicalDocument(
                        CHUNK_ID.toString(),
                        evidence,
                        "Probation policy is sixty days",
                        Map.of("title", "Employee handbook"))));
        vectors.stageUpsert(
                first,
                List.of(new VectorIndex.VectorRecord(
                        "chunk-vector",
                        CHUNK_ID.toString(),
                        evidence,
                        VectorIndex.VectorKind.CHUNK,
                        PROFILE_ID,
                        "test-embedding",
                        vector(1, 0, 0),
                        Map.of())));
        graph.stageReplaceRevision(first, graphRevision(first.generation()));
        markPrepared(first);
        ProjectionSnapshot firstSnapshot = publications.publish(first, NOW);

        AuthorizedEvidenceScope allowed = scope(Set.of(ASSET_ID));
        AuthorizedEvidenceScope denied = scope(Set.of());
        assertEquals(
                "Probation policy is sixty days",
                content.get(allowed, firstSnapshot, CHUNK_ID.toString())
                        .orElseThrow()
                        .content());
        assertEquals(
                CHUNK_ID.toString(),
                lexical.search(
                                allowed,
                                firstSnapshot,
                                new LexicalIndex.SearchRequest(
                                        "probation", Set.of(), 10, 0, null))
                        .hits()
                        .getFirst()
                        .id());
        assertEquals(
                CHUNK_ID.toString(),
                vectors.search(
                                allowed,
                                firstSnapshot,
                                new VectorIndex.SearchRequest(
                                        PROFILE_ID,
                                        Set.of(VectorIndex.VectorKind.CHUNK),
                                        Set.of(),
                                        vector(1, 0, 0),
                                        10,
                                        0))
                        .getFirst()
                        .subjectId());
        assertEquals(
                List.of(ENTITY_A_ID, ENTITY_B_ID),
                graph.expandEntityIds(
                        allowed,
                        firstSnapshot,
                        List.of(ENTITY_A_ID),
                        1,
                        10));
        assertTrue(content.get(denied, firstSnapshot, CHUNK_ID.toString()).isEmpty());
        assertTrue(lexical.search(
                        denied,
                        firstSnapshot,
                        new LexicalIndex.SearchRequest(
                                "probation", Set.of(), 10, 0, null))
                .hits()
                .isEmpty());
        assertTrue(vectors.search(
                        denied,
                        firstSnapshot,
                        new VectorIndex.SearchRequest(
                                PROFILE_ID,
                                Set.of(VectorIndex.VectorKind.CHUNK),
                                Set.of(),
                                vector(1, 0, 0),
                                10,
                                0))
                .isEmpty());
        assertTrue(graph.loadEntities(
                        denied, firstSnapshot, List.of(ENTITY_A_ID))
                .isEmpty());

        ProjectionBatch second = batch("second", 1);
        content.stageDelete(second, List.of(CHUNK_ID.toString()));
        lexical.stageDelete(second, List.of(CHUNK_ID.toString()));
        vectors.stageDelete(second, List.of("chunk-vector"));
        graph.stageDeleteRevision(second, REVISION_ID);
        markPrepared(second);
        ProjectionSnapshot secondSnapshot =
                publications.publish(second, NOW.plusSeconds(1));

        assertTrue(content.get(allowed, secondSnapshot, CHUNK_ID.toString()).isEmpty());
        assertTrue(graph.loadEntities(
                        allowed, secondSnapshot, List.of(ENTITY_A_ID))
                .isEmpty());
        assertEquals(
                "Probation policy is sixty days",
                content.get(allowed, firstSnapshot, CHUNK_ID.toString())
                        .orElseThrow()
                        .content());
        assertEquals(
                List.of(ENTITY_A_ID, ENTITY_B_ID),
                graph.expandEntityIds(
                        allowed,
                        firstSnapshot,
                        List.of(ENTITY_A_ID),
                        1,
                        10));
    }

    @Test
    void losingPreparedBatchNeverLeaksAndCanBeDiscarded() {
        ProjectionNamespace namespace =
                new ProjectionNamespace(ORGANIZATION_ID, "competing", "knowledge");
        ProjectionBatch winner = contentOnlyBatch(namespace, "winner");
        ProjectionBatch loser = contentOnlyBatch(namespace, "loser");
        content.stageUpsert(
                winner,
                List.of(contentRecord("winner-record", ASSET_ID)));
        content.stageUpsert(
                loser,
                List.of(contentRecord("loser-record", ASSET_ID)));
        publications.markPrepared(winner, ProjectionKind.CONTENT, NOW);
        publications.markPrepared(loser, ProjectionKind.CONTENT, NOW);

        ProjectionSnapshot snapshot = publications.publish(winner, NOW);
        assertThrows(
                PublicationConflictException.class,
                () -> publications.publish(loser, NOW));
        assertTrue(content.get(scope(Set.of(ASSET_ID)), snapshot, "winner-record")
                .isPresent());
        assertTrue(content.get(scope(Set.of(ASSET_ID)), snapshot, "loser-record")
                .isEmpty());

        publications.abort(loser, "lost publication race", NOW);
        content.discard(loser);
        assertEquals(
                0,
                plainJdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM projection_content_records
                        WHERE batch_id = ?
                        """,
                        Integer.class,
                        loser.id()));
    }

    private static ProjectionBatch batch(String key, long previous) {
        return new ProjectionBatch(
                id("shared-snapshot-batch-" + key),
                NAMESPACE,
                previous,
                previous + 1,
                "shared-snapshot-" + key,
                "manifest-" + key,
                Set.of(
                        ProjectionKind.CONTENT,
                        ProjectionKind.LEXICAL,
                        ProjectionKind.VECTOR,
                        ProjectionKind.GRAPH),
                NOW.plusSeconds(previous));
    }

    private static ProjectionBatch contentOnlyBatch(
            ProjectionNamespace namespace,
            String key) {
        return new ProjectionBatch(
                id("competing-" + key),
                namespace,
                0,
                1,
                "competing-" + key,
                "manifest-competing-" + key,
                Set.of(ProjectionKind.CONTENT),
                NOW);
    }

    private static void markPrepared(ProjectionBatch batch) {
        batch.requiredProjections()
                .forEach(kind -> publications.markPrepared(batch, kind, NOW));
    }

    private static GraphRevisionContributions graphRevision(long generation) {
        CanonicalEntity entityA = new CanonicalEntity(ENTITY_A_ID, "Employee");
        CanonicalEntity entityB = new CanonicalEntity(ENTITY_B_ID, "Probation Policy");
        EvidenceProvenance provenance = new EvidenceProvenance(
                evidence(),
                generation,
                "test",
                "test-model",
                "test-prompt",
                1,
                NOW);
        EntityContribution contributionA = new EntityContribution(
                id("entity-contribution-a"),
                entityA,
                "PERSON",
                "Employee",
                provenance);
        EntityContribution contributionB = new EntityContribution(
                id("entity-contribution-b"),
                entityB,
                "POLICY",
                "Probation policy",
                provenance);
        RelationContribution relation = new RelationContribution(
                id("relation-contribution"),
                new CanonicalRelation(
                        RELATION_ID,
                        ENTITY_A_ID,
                        ENTITY_B_ID,
                        RelationOrientation.DIRECTED),
                "GOVERNED_BY",
                List.of("probation"),
                "Employee is governed by probation policy",
                1,
                provenance);
        return new GraphRevisionContributions(
                ORGANIZATION_ID,
                ASSET_ID,
                REVISION_ID,
                generation,
                List.of(contributionA, contributionB),
                List.of(relation));
    }

    private static AuthorizedEvidenceScope scope(Set<UUID> assetIds) {
        return new AuthorizedEvidenceScope(
                ORGANIZATION_ID,
                ACTOR_ID,
                null,
                false,
                assetIds,
                "model-v1",
                1,
                NOW);
    }

    private static EvidenceReference evidence() {
        return new EvidenceReference(
                ORGANIZATION_ID,
                ASSET_ID,
                REVISION_ID,
                CHUNK_ID,
                ACL_ID,
                1);
    }

    private static ContentStore.ContentRecord contentRecord(
            String id,
            UUID assetId) {
        return new ContentStore.ContentRecord(
                id,
                new EvidenceReference(
                        ORGANIZATION_ID,
                        assetId,
                        id(id + "-revision"),
                        id(id + "-chunk"),
                        id(id + "-acl"),
                        1),
                ContentStore.ContentKind.CHUNK,
                id,
                1,
                Map.of());
    }

    private static FloatVector vector(float... values) {
        return new FloatVector(values);
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
