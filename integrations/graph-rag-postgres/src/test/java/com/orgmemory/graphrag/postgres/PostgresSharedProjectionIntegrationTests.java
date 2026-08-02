package com.orgmemory.graphrag.postgres;

import static com.orgmemory.graphrag.testkit.ProjectionPermitFixtures.commitPermit;
import static com.orgmemory.graphrag.testkit.ProjectionPermitFixtures.discardPermit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.export.GraphExportReader;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import com.orgmemory.graphrag.query.AuthorizedGraphTraversal;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.LexicalIndex;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore.PublicationConflictException;
import com.orgmemory.graphrag.storage.VectorIndex;
import com.orgmemory.graphrag.testkit.GraphStoreConformance;
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
import org.springframework.dao.DataAccessException;
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
    private static DataSource dataSource;
    private static JdbcTemplate plainJdbc;
    private static PostgresContentStore content;
    private static PostgresLexicalIndex lexical;
    private static PostgresVectorIndex vectors;
    private static PostgresGraphStore graph;
    private static AuthorizedGraphTraversal traversal;
    private static GraphExportReader graphExport;

    @BeforeAll
    static void migrate() {
        dataSource = new DriverManagerDataSource(
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
                       (?, 'Shared snapshot', now(), now(), 0),
                       (?, 'Graph store conformance', now(), now(), 0)
                """,
                CONFORMANCE_ORGANIZATION_ID,
                ORGANIZATION_ID,
                GraphStoreConformance.organizationId());
        NamedParameterJdbcTemplate jdbc =
                new NamedParameterJdbcTemplate(dataSource);
        DataSourceTransactionManager transactions =
                new DataSourceTransactionManager(dataSource);
        publications = new PostgresProjectionPublicationStore(jdbc, transactions);
        content = new PostgresContentStore(jdbc, transactions, publications);
        lexical = new PostgresLexicalIndex(jdbc, transactions, publications);
        vectors = new PostgresVectorIndex(jdbc, transactions, publications);
        graph = new PostgresGraphStore(jdbc, transactions, publications);
        traversal = new AuthorizedGraphTraversal(graph);
        graphExport = new PostgresGraphExportReader(
                jdbc,
                graph,
                publications,
                new PostgresGraphCurationStore(jdbc, transactions));
    }

    @Test
    void publicationStorePassesSharedConformance() {
        ProjectionPublicationConformance.verify(() -> publications);
    }

    @Test
    void graphStorePassesSharedSecurityAndLifecycleConformance() {
        GraphStoreConformance.verify(graph, publications);
    }

    @Test
    void durableReceiptCanBePublishedAfterStoreRecreation() {
        ProjectionNamespace namespace = new ProjectionNamespace(
                id("postgres-receipt-restart-organization"),
                "restart",
                "knowledge");
        plainJdbc.update(
                """
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, 'Projection receipt restart', now(), now(), 0)
                """,
                namespace.organizationId());
        ProjectionBatch batch = contentOnlyBatch(namespace, "receipt-restart");
        publications.markPrepared(batch, ProjectionKind.CONTENT, NOW);

        PostgresProjectionPublicationStore restarted =
                new PostgresProjectionPublicationStore(
                        new NamedParameterJdbcTemplate(dataSource),
                        new DataSourceTransactionManager(dataSource));
        ProjectionSnapshot snapshot = restarted.publish(
                batch, commitPermit(batch, NOW.plusSeconds(1)), NOW.plusSeconds(1));

        assertEquals(batch.id(), snapshot.batchId());
        assertEquals(snapshot, restarted.current(namespace).orElseThrow());
    }

    @Test
    void activeIdempotencyKeyRejectsASecondRandomBatchAfterRestart() {
        ProjectionNamespace namespace = new ProjectionNamespace(
                id("postgres-orphan-restart-organization"),
                "restart",
                "knowledge");
        plainJdbc.update(
                """
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, 'Projection orphan restart', now(), now(), 0)
                """,
                namespace.organizationId());
        ProjectionBatch original = contentOnlyBatch(namespace, "orphan-restart");
        publications.markPrepared(original, ProjectionKind.CONTENT, NOW);
        ProjectionBatch replacement = new ProjectionBatch(
                UUID.randomUUID(),
                original.namespace(),
                original.expectedPreviousGeneration(),
                original.generation(),
                original.idempotencyKey(),
                original.manifestFingerprint(),
                original.requiredProjections(),
                NOW.plusSeconds(1));

        PostgresProjectionPublicationStore restarted =
                new PostgresProjectionPublicationStore(
                        new NamedParameterJdbcTemplate(dataSource),
                        new DataSourceTransactionManager(dataSource));

        assertThrows(
                PublicationConflictException.class,
                () -> restarted.markPrepared(
                        replacement,
                        ProjectionKind.CONTENT,
                        NOW.plusSeconds(1)));
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
        ProjectionSnapshot firstSnapshot = publications.publish(
                first, commitPermit(first, NOW), NOW);

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
                traversal.expandEntityIds(
                        allowed,
                        firstSnapshot,
                        List.of(ENTITY_A_ID),
                        1,
                        10));
        assertEquals(
                2,
                graphExport.read(allowed, NAMESPACE).entities().size(),
                "graph export must read the same published shared snapshot");
        assertTrue(
                graphExport.read(denied, NAMESPACE).entities().isEmpty(),
                "graph export must preserve the exact authorized asset scope");
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
                publications.publish(
                        second, commitPermit(second, NOW.plusSeconds(1)), NOW.plusSeconds(1));

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
                traversal.expandEntityIds(
                        allowed,
                        firstSnapshot,
                        List.of(ENTITY_A_ID),
                        1,
                        10));
    }

    @Test
    void boundedAdaptersWriteMultipleRowsAndGraphDependenciesInPhases() {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(plainJdbc);
        DataSourceTransactionManager transactions =
                new DataSourceTransactionManager(plainJdbc.getDataSource());
        PostgresContentStore boundedContent =
                new PostgresContentStore(jdbc, transactions, publications, 2);
        PostgresLexicalIndex boundedLexical =
                new PostgresLexicalIndex(jdbc, transactions, publications, 2);
        PostgresVectorIndex boundedVectors =
                new PostgresVectorIndex(jdbc, transactions, publications, 2);
        PostgresGraphStore boundedGraph =
                new PostgresGraphStore(jdbc, transactions, publications, 1);
        ProjectionNamespace namespace =
                new ProjectionNamespace(ORGANIZATION_ID, "bounded", "knowledge");
        ProjectionBatch batch = allProjectionBatch(namespace, "bounded-multi-row");
        List<String> ids = List.of("bounded-1", "bounded-2", "bounded-3");

        boundedContent.stageUpsert(
                batch,
                ids.stream()
                        .map(id -> new ContentStore.ContentRecord(
                                id,
                                evidence(),
                                ContentStore.ContentKind.CHUNK,
                                "Bounded policy " + id,
                                3,
                                Map.of("title", id)))
                        .toList());
        boundedLexical.stageUpsert(
                batch,
                ids.stream()
                        .map(id -> new LexicalIndex.LexicalDocument(
                                id,
                                evidence(),
                                "Bounded policy " + id,
                                Map.of("title", id)))
                        .toList());
        boundedVectors.stageUpsert(
                batch,
                ids.stream()
                        .map(id -> new VectorIndex.VectorRecord(
                                id,
                                id,
                                evidence(),
                                VectorIndex.VectorKind.CHUNK,
                                PROFILE_ID,
                                "test-embedding",
                                vector(1, 0, 0),
                                Map.of()))
                        .toList());
        boundedGraph.stageReplaceRevision(batch, graphRevision(batch.generation()));
        markPrepared(batch);
        ProjectionSnapshot snapshot = publications.publish(
                batch, commitPermit(batch, NOW.plusSeconds(2)), NOW.plusSeconds(2));

        assertEquals(
                ids,
                boundedContent.get(scope(Set.of(ASSET_ID)), snapshot, ids).stream()
                        .map(ContentStore.ContentRecord::id)
                        .toList());
        assertEquals(
                3,
                plainJdbc.queryForObject(
                        "SELECT count(*) FROM projection_lexical_documents WHERE batch_id = ?",
                        Integer.class,
                        batch.id()));
        assertEquals(
                3,
                plainJdbc.queryForObject(
                        "SELECT count(*) FROM projection_vector_records WHERE batch_id = ?",
                        Integer.class,
                        batch.id()));
        assertEquals(
                0,
                plainJdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM projection_graph_entity_contributions contribution
                        LEFT JOIN projection_graph_entities entity
                          ON entity.batch_id = contribution.batch_id
                         AND entity.entity_id = contribution.entity_id
                        WHERE contribution.batch_id = ? AND entity.entity_id IS NULL
                        """,
                        Integer.class,
                        batch.id()));
        assertEquals(
                0,
                plainJdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM projection_graph_relation_contributions contribution
                        LEFT JOIN projection_graph_relations relation
                          ON relation.batch_id = contribution.batch_id
                         AND relation.relation_id = contribution.relation_id
                        WHERE contribution.batch_id = ? AND relation.relation_id IS NULL
                        """,
                        Integer.class,
                        batch.id()));
    }

    @Test
    void laterSubBatchFailureRollsBackTheWholeStage() {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(plainJdbc);
        DataSourceTransactionManager transactions =
                new DataSourceTransactionManager(plainJdbc.getDataSource());
        PostgresContentStore boundedContent =
                new PostgresContentStore(jdbc, transactions, publications, 2);
        ProjectionNamespace namespace =
                new ProjectionNamespace(ORGANIZATION_ID, "batch-failure", "knowledge");
        ProjectionBatch batch = contentOnlyBatch(namespace, "later-sub-batch-failure");
        List<ContentStore.ContentRecord> records = List.of(
                contentRecord("valid-1", ASSET_ID),
                contentRecord("valid-2", ASSET_ID),
                contentRecord("x".repeat(256), ASSET_ID),
                contentRecord("valid-4", ASSET_ID));

        assertThrows(
                DataAccessException.class,
                () -> boundedContent.stageUpsert(batch, records));

        assertEquals(
                0,
                plainJdbc.queryForObject(
                        "SELECT count(*) FROM projection_content_records WHERE batch_id = ?",
                        Integer.class,
                        batch.id()));
        assertEquals(
                0,
                plainJdbc.queryForObject(
                        "SELECT count(*) FROM projection_batches WHERE batch_id = ?",
                        Integer.class,
                        batch.id()));
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

        ProjectionSnapshot snapshot = publications.publish(
                winner, commitPermit(winner, NOW), NOW);
        assertThrows(
                PublicationConflictException.class,
                () -> publications.publish(loser, commitPermit(loser, NOW), NOW));
        assertTrue(content.get(scope(Set.of(ASSET_ID)), snapshot, "winner-record")
                .isPresent());
        assertTrue(content.get(scope(Set.of(ASSET_ID)), snapshot, "loser-record")
                .isEmpty());

        publications.abort(loser, "lost publication race", NOW);
        content.discard(loser, discardPermit(loser, NOW));
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

    private static ProjectionBatch allProjectionBatch(
            ProjectionNamespace namespace,
            String key) {
        return new ProjectionBatch(
                id(key),
                namespace,
                0,
                1,
                key,
                "manifest-" + key,
                Set.of(
                        ProjectionKind.CONTENT,
                        ProjectionKind.LEXICAL,
                        ProjectionKind.VECTOR,
                        ProjectionKind.GRAPH),
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
