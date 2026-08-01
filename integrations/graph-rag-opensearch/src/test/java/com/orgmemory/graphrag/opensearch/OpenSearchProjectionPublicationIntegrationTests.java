package com.orgmemory.graphrag.opensearch;

import static com.orgmemory.graphrag.testkit.ProjectionPermitFixtures.commitPermit;
import static com.orgmemory.graphrag.testkit.ProjectionPermitFixtures.discardPermit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.orgmemory.graphrag.query.AuthorizedGraphTraversal;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.LexicalIndex;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionBatchLifecycle;
import com.orgmemory.graphrag.storage.ProjectionDiscardPermit;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore.PublicationConflictException;
import com.orgmemory.graphrag.storage.ProcessingStatusIndex;
import com.orgmemory.graphrag.storage.VectorIndex;
import com.orgmemory.graphrag.testkit.ProjectionPublicationConformance;
import com.orgmemory.graphrag.testkit.GraphStoreConformance;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class OpenSearchProjectionPublicationIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-07-24T08:00:00Z");
    private static final UUID ORGANIZATION_ID = id("opensearch-shared-organization");
    private static final UUID ACTOR_ID = id("opensearch-shared-actor");
    private static final UUID ASSET_ID = id("opensearch-shared-asset");
    private static final UUID REVISION_ID = id("opensearch-shared-revision");
    private static final UUID CHUNK_ID = id("opensearch-shared-chunk");
    private static final UUID ACL_ID = id("opensearch-shared-acl");
    private static final UUID PROFILE_ID = id("opensearch-shared-profile");
    private static final UUID ENTITY_A_ID = id("opensearch-shared-entity-a");
    private static final UUID ENTITY_B_ID = id("opensearch-shared-entity-b");
    private static final UUID RELATION_ID = id("opensearch-shared-relation");
    private static final ProjectionNamespace NAMESPACE =
            new ProjectionNamespace(ORGANIZATION_ID, "default", "knowledge");

    @Container
    static final GenericContainer<?> opensearch =
            new GenericContainer<>(
                            DockerImageName.parse("opensearchproject/opensearch:3.7.0"))
                    .withEnv("discovery.type", "single-node")
                    .withEnv("DISABLE_SECURITY_PLUGIN", "true")
                    .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
                    .withExposedPorts(9200)
                    .waitingFor(Wait.forHttp("/")
                            .forStatusCodeMatching(
                                    status -> status == 200 || status == 401)
                            .withStartupTimeout(Duration.ofMinutes(2)));

    private static OpenSearchTestClient testClient;
    private static OpenSearchProjectionPublicationStore publications;
    private static OpenSearchOperations operations;
    private static OpenSearchCopyForwardCoordinator copyForward;
    private static OpenSearchIndexNames indexes;
    private static OpenSearchContentStore content;
    private static OpenSearchLexicalIndex lexical;
    private static OpenSearchVectorIndex vectors;
    private static OpenSearchGraphStore graph;
    private static AuthorizedGraphTraversal traversal;
    private static OpenSearchProcessingStatusIndex statuses;

    @BeforeAll
    static void createClient() {
        testClient = new OpenSearchTestClient(
                opensearch.getHost(),
                opensearch.getMappedPort(9200));
        OpenSearchGraphRagProperties properties = new OpenSearchGraphRagProperties();
        properties.setIndexPrefix("orgmemory-publication-test");
        indexes = new OpenSearchIndexNames(properties.getIndexPrefix());
        operations = new OpenSearchOperations(
                testClient.client(),
                properties.getBulkMaximumOperations());
        publications = new OpenSearchProjectionPublicationStore(operations, indexes);
        copyForward = new OpenSearchCopyForwardCoordinator(
                operations,
                indexes.control(),
                properties.getCopyMaximumBytes());
        content = new OpenSearchContentStore(operations, publications, indexes, copyForward);
        lexical = new OpenSearchLexicalIndex(operations, publications, indexes, copyForward);
        vectors = new OpenSearchVectorIndex(operations, publications, indexes, copyForward);
        graph = new OpenSearchGraphStore(
                operations,
                publications,
                indexes,
                copyForward);
        traversal = new AuthorizedGraphTraversal(graph);
        statuses = new OpenSearchProcessingStatusIndex(operations, indexes);
    }

    @AfterAll
    static void closeClient() throws Exception {
        testClient.close();
    }

    @Test
    void publicationStorePassesSharedConformance() {
        ProjectionPublicationConformance.verify(() -> publications);
    }

    @Test
    void graphStorePassesSharedSecurityLifecycleAndTraversalConformance() {
        GraphStoreConformance.verify(graph, publications);
    }

    @Test
    void previousHeadIsBackfilledBeforeTheNextGenerationBecomesCurrent() {
        ProjectionNamespace namespace = new ProjectionNamespace(
                id("history-organization"),
                "default",
                "knowledge");
        ProjectionBatch first = batch(namespace, "first", 0);
        publications.markPrepared(first, ProjectionKind.CONTENT, NOW);
        var firstSnapshot = publications.publish(first, commitPermit(first, NOW), NOW);

        ProjectionBatch second = batch(namespace, "second", 1);
        publications.markPrepared(second, ProjectionKind.CONTENT, NOW.plusSeconds(1));
        var secondSnapshot = publications.publish(
                second, commitPermit(second, NOW.plusSeconds(1)), NOW.plusSeconds(1));

        assertEquals(firstSnapshot, publications.published(namespace, 1).orElseThrow());
        assertEquals(secondSnapshot, publications.current(namespace).orElseThrow());
    }

    @Test
    void competingGenerationCannotReplaceTheWinner() {
        ProjectionNamespace namespace = new ProjectionNamespace(
                id("race-organization"),
                "default",
                "knowledge");
        ProjectionBatch winner = batch(namespace, "winner", 0);
        ProjectionBatch loser = batch(namespace, "loser", 0);
        publications.markPrepared(winner, ProjectionKind.CONTENT, NOW);
        publications.markPrepared(loser, ProjectionKind.CONTENT, NOW);

        var snapshot = publications.publish(winner, commitPermit(winner, NOW), NOW);
        assertThrows(
                PublicationConflictException.class,
                () -> publications.publish(
                        loser, commitPermit(loser, NOW.plusSeconds(1)), NOW.plusSeconds(1)));
        assertEquals(snapshot, publications.current(namespace).orElseThrow());
    }

    @Test
    void committingBatchCannotBeAborted() {
        ProjectionNamespace namespace = new ProjectionNamespace(
                id("committing-abort-organization"),
                "default",
                "knowledge");
        ProjectionBatch batch = batch(namespace, "committing", 0);
        publications.markPrepared(batch, ProjectionKind.CONTENT, NOW);
        OpenSearchOperations.VersionedDocument registered =
                operations.get(indexes.control(), "batch:" + batch.id());
        Map<String, Object> committing = new java.util.LinkedHashMap<>(
                registered.source());
        committing.put("status", "COMMITTING");
        assertTrue(operations.compareAndSet(
                indexes.control(),
                "batch:" + batch.id(),
                registered,
                committing));

        assertThrows(
                PublicationConflictException.class,
                () -> publications.abort(batch, "cancelled too late", NOW));
    }

    @Test
    void durableReceiptCanBePublishedAfterStoreRecreation() {
        ProjectionNamespace namespace = new ProjectionNamespace(
                id("receipt-restart-organization"),
                "default",
                "knowledge");
        ProjectionBatch batch = batch(namespace, "receipt-restart", 0);
        publications.markPrepared(batch, ProjectionKind.CONTENT, NOW);

        OpenSearchProjectionPublicationStore restarted =
                new OpenSearchProjectionPublicationStore(operations, indexes);
        ProjectionSnapshot snapshot = restarted.publish(
                batch, commitPermit(batch, NOW.plusSeconds(1)), NOW.plusSeconds(1));

        assertEquals(batch.id(), snapshot.batchId());
        assertEquals(snapshot, restarted.current(namespace).orElseThrow());
    }

    @Test
    void visibleHeadRemainsAuthoritativeWhenBatchMarkerLooksCommitting() {
        ProjectionNamespace namespace = new ProjectionNamespace(
                id("committing-replay-organization"),
                "default",
                "knowledge");
        ProjectionBatch batch = batch(namespace, "committing-replay", 0);
        publications.markPrepared(batch, ProjectionKind.CONTENT, NOW);
        ProjectionSnapshot published = publications.publish(
                batch, commitPermit(batch, NOW), NOW);
        OpenSearchOperations.VersionedDocument registered =
                operations.get(indexes.control(), "batch:" + batch.id());
        Map<String, Object> committing = new LinkedHashMap<>(registered.source());
        committing.put("status", "COMMITTING");
        assertTrue(operations.compareAndSet(
                indexes.control(),
                "batch:" + batch.id(),
                registered,
                committing));

        OpenSearchProjectionPublicationStore restarted =
                new OpenSearchProjectionPublicationStore(operations, indexes);
        ProjectionSnapshot replay = restarted.publish(
                batch, commitPermit(batch, NOW.plusSeconds(1)), NOW.plusSeconds(1));

        assertEquals(published, replay);
        assertEquals(published, restarted.current(namespace).orElseThrow());
        assertThrows(
                PublicationConflictException.class,
                () -> restarted.abort(batch, "must not discard visible data", NOW));
    }

    @Test
    void crashAfterHeadWriteKeepsStagingAndRestartFinalizesWithoutRestaging() {
        ProjectionNamespace namespace = new ProjectionNamespace(
                id("head-crash-organization"), "default", "knowledge");
        ProjectionBatch batch = batch(namespace, "head-crash", 0);
        OpenSearchOperations crashingOperations =
                new CrashAfterHeadCreateOperations(operations);
        OpenSearchProjectionPublicationStore crashingStore =
                new OpenSearchProjectionPublicationStore(crashingOperations, indexes);
        ProjectionBatchLifecycle.Preparation firstPreparation =
                preparationThatMustNotDiscard();

        assertThrows(
                PublicationCrash.class,
                () -> new ProjectionBatchLifecycle(crashingStore).publish(
                        batch,
                        List.of(firstPreparation),
                        candidate -> commitPermit(candidate, NOW),
                        NOW));

        assertEquals(batch.id(), crashingStore.current(namespace).orElseThrow().batchId());
        OpenSearchOperations.VersionedDocument marker =
                operations.get(indexes.control(), "batch:" + batch.id());
        assertEquals("COMMITTING", marker.source().get("status"));

        OpenSearchProjectionPublicationStore restarted =
                new OpenSearchProjectionPublicationStore(operations, indexes);
        ProjectionSnapshot replay = new ProjectionBatchLifecycle(restarted).publish(
                batch,
                List.of(preparationThatMustNotRun()),
                candidate -> commitPermit(candidate, NOW),
                NOW.plusSeconds(1));

        assertEquals(batch.id(), replay.batchId());
        assertEquals(
                "PUBLISHED",
                operations.get(indexes.control(), "batch:" + batch.id())
                        .source()
                        .get("status"));
    }

    @Test
    void adaptersUseOneAuthorizedSnapshotAndRetainHistoricalReads() {
        ProjectionBatch first = sharedBatch("first", 0);
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
                lexical.search(
                                allowed,
                                firstSnapshot,
                                new LexicalIndex.SearchRequest(
                                        "employee",
                                        Set.of("title"),
                                        10,
                                        0,
                                        null))
                        .hits()
                        .getFirst()
                        .id());
        assertTrue(lexical.search(
                        allowed,
                        firstSnapshot,
                        new LexicalIndex.SearchRequest(
                                "probation",
                                Set.of("title"),
                                10,
                                0,
                                null))
                .hits()
                .isEmpty());
        assertEquals(
                1,
                lexical.search(
                                allowed,
                                firstSnapshot,
                                new LexicalIndex.SearchRequest(
                                        "probation",
                                        Set.of(),
                                        10_001,
                                        0,
                                        null))
                        .hits()
                        .size());
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
                0.0,
                vectors.search(
                                allowed,
                                firstSnapshot,
                                new VectorIndex.SearchRequest(
                                        PROFILE_ID,
                                        Set.of(VectorIndex.VectorKind.CHUNK),
                                        Set.of(),
                                        vector(0, 0, 0),
                                        10,
                                        0))
                        .getFirst()
                        .similarity(),
                0.000_000_1);
        assertEquals(
                0.0,
                vectors.search(
                                allowed,
                                firstSnapshot,
                                new VectorIndex.SearchRequest(
                                        PROFILE_ID,
                                        Set.of(VectorIndex.VectorKind.CHUNK),
                                        Set.of(),
                                        vector(0, 1, 0),
                                        10,
                                        -1))
                        .getFirst()
                        .similarity(),
                0.000_000_1);
        assertEquals(
                List.of(ENTITY_A_ID, ENTITY_B_ID),
                traversal.expandEntityIds(
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
                        denied,
                        firstSnapshot,
                        List.of(ENTITY_A_ID))
                .isEmpty());

        ProjectionBatch second = sharedBatch("second", 1);
        content.stageDelete(second, List.of(CHUNK_ID.toString()));
        lexical.stageDelete(second, List.of(CHUNK_ID.toString()));
        vectors.stageDelete(second, List.of("chunk-vector"));
        graph.stageDeleteRevision(second, REVISION_ID);
        markPrepared(second);
        ProjectionSnapshot secondSnapshot =
                publications.publish(
                        second, commitPermit(second, NOW.plusSeconds(1)), NOW.plusSeconds(1));

        assertTrue(content.get(allowed, secondSnapshot, CHUNK_ID.toString()).isEmpty());
        assertTrue(lexical.search(
                        allowed,
                        secondSnapshot,
                        new LexicalIndex.SearchRequest(
                                "probation", Set.of(), 10, 0, null))
                .hits()
                .isEmpty());
        assertTrue(vectors.search(
                        allowed,
                        secondSnapshot,
                        new VectorIndex.SearchRequest(
                                PROFILE_ID,
                                Set.of(VectorIndex.VectorKind.CHUNK),
                                Set.of(),
                                vector(1, 0, 0),
                                10,
                                0))
                .isEmpty());
        assertTrue(graph.loadEntities(
                        allowed,
                        secondSnapshot,
                        List.of(ENTITY_A_ID))
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
    void graphCopyForwardPreservesUnchangedEntityAndRelationPartitions() {
        UUID retainedRevision = id("graph-copy-retained-revision");
        UUID changedRevision = id("graph-copy-changed-revision");
        UUID retainedSource = id("graph-copy-retained-source");
        UUID retainedTarget = id("graph-copy-retained-target");
        UUID retainedRelation = id("graph-copy-retained-relation");
        ProjectionNamespace namespace =
                new ProjectionNamespace(ORGANIZATION_ID, "graph-copy", "knowledge");
        ProjectionBatch first = graphOnlyBatch(namespace, "first", 0);
        graph.stageReplaceRevision(
                first,
                graphRevision(
                        ASSET_ID,
                        retainedRevision,
                        List.of(
                                new CanonicalEntity(retainedSource, "Retained source"),
                                new CanonicalEntity(retainedTarget, "Retained target")),
                        List.of(new CanonicalRelation(
                                retainedRelation,
                                retainedSource,
                                retainedTarget,
                                RelationOrientation.DIRECTED))));
        graph.stageReplaceRevision(
                first,
                graphRevision(
                        ASSET_ID,
                        changedRevision,
                        List.of(new CanonicalEntity(ENTITY_A_ID, "Original entity")),
                        List.of()));
        publications.markPrepared(first, ProjectionKind.GRAPH, NOW);
        publications.publish(first, commitPermit(first, NOW), NOW);

        ProjectionBatch second = graphOnlyBatch(namespace, "second", 1);
        graph.stageReplaceRevision(
                second,
                graphRevision(
                        ASSET_ID,
                        changedRevision,
                        List.of(new CanonicalEntity(ENTITY_B_ID, "Replacement entity")),
                        List.of()));
        publications.markPrepared(second, ProjectionKind.GRAPH, NOW.plusSeconds(1));
        ProjectionSnapshot snapshot = publications.publish(
                second, commitPermit(second, NOW.plusSeconds(1)), NOW.plusSeconds(1));

        assertEquals(
                Set.of(retainedSource, retainedTarget),
                graph.loadEntities(
                                scope(Set.of(ASSET_ID)),
                                snapshot,
                                List.of(retainedSource, retainedTarget))
                        .stream()
                        .map(CanonicalEntity::id)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        assertEquals(
                List.of(retainedRelation),
                graph.loadRelations(
                                scope(Set.of(ASSET_ID)),
                                snapshot,
                                List.of(retainedRelation))
                        .stream()
                        .map(CanonicalRelation::id)
                        .toList());
    }

    @Test
    void copyForwardPreservesEverySourceByteExceptGenerationCoordinatesAndRequiredIds()
            throws Exception {
        ProjectionNamespace namespace = new ProjectionNamespace(
                ORGANIZATION_ID,
                "copy-source-identity",
                "knowledge");
        ProjectionBatch first = copyBatch(namespace, "first", 0);
        ProjectionBatch second = copyBatch(namespace, "second", 1);
        EvidenceReference evidence = new EvidenceReference(
                ORGANIZATION_ID,
                ASSET_ID,
                id("copy-source-revision"),
                id("copy-source-chunk"),
                id("copy-source-acl"),
                7);

        ContentStore.ContentRecord contentRecord = new ContentStore.ContentRecord(
                "content-copy-id",
                evidence,
                ContentStore.ContentKind.CHUNK,
                "Expense policy identity payload",
                17,
                Map.of());
        LexicalIndex.LexicalDocument lexicalDocument = new LexicalIndex.LexicalDocument(
                "lexical-copy-id",
                evidence,
                "Expense policy identity payload",
                Map.of("title", "Expense policy"));
        VectorIndex.VectorRecord vectorRecord = new VectorIndex.VectorRecord(
                "vector-copy-id",
                "content-copy-id",
                evidence,
                VectorIndex.VectorKind.CHUNK,
                PROFILE_ID,
                "identity-model",
                vector(0.125f, -0.5f, 0.875f),
                Map.of());

        content.stageUpsert(first, List.of(contentRecord));
        lexical.stageUpsert(first, List.of(lexicalDocument));
        vectors.stageUpsert(first, List.of(vectorRecord));

        Map<String, Object> nestedPayload = new LinkedHashMap<>();
        nestedPayload.put("flags", List.of(true, false));
        nestedPayload.put("nested", Map.of("count", 9L, "ratio", 1.25d));
        nestedPayload.put("labels", List.of("finance", "restricted"));
        Map<String, Object> contentSource = new LinkedHashMap<>(
                OpenSearchProjectionCodec.content(first, contentRecord));
        contentSource.put("metadata", nestedPayload);
        Map<String, Object> lexicalSource = new LinkedHashMap<>(
                OpenSearchProjectionCodec.lexical(first, lexicalDocument));
        lexicalSource.put("fields", nestedPayload);
        Map<String, Object> vectorSource = new LinkedHashMap<>(
                OpenSearchProjectionCodec.vector(first, vectorRecord));
        vectorSource.put("metadata", nestedPayload);
        operations.index(
                indexes.content(),
                OpenSearchStagedIndex.physicalId(first.id(), contentRecord.id()),
                contentSource);
        operations.index(indexes.lexical(first.id()), lexicalDocument.id(), lexicalSource);
        String vectorIndex = indexes.vectors(PROFILE_ID, vectorRecord.vector().dimensions());
        operations.index(
                vectorIndex,
                OpenSearchStagedIndex.physicalId(first.id(), vectorRecord.id()),
                vectorSource);

        first.requiredProjections()
                .forEach(kind -> publications.markPrepared(first, kind, NOW));
        publications.publish(first, commitPermit(first, NOW), NOW);

        content.stageDelete(second, List.of());
        lexical.stageDelete(second, List.of());
        vectors.stageDelete(second, List.of());

        assertCopiedSource(
                indexes.content(),
                OpenSearchStagedIndex.physicalId(first.id(), contentRecord.id()),
                indexes.content(),
                OpenSearchStagedIndex.physicalId(second.id(), contentRecord.id()),
                second);
        assertCopiedSource(
                indexes.lexical(first.id()),
                lexicalDocument.id(),
                indexes.lexical(second.id()),
                lexicalDocument.id(),
                second);
        assertCopiedSource(
                vectorIndex,
                OpenSearchStagedIndex.physicalId(first.id(), vectorRecord.id()),
                vectorIndex,
                OpenSearchStagedIndex.physicalId(second.id(), vectorRecord.id()),
                second);
    }

    @Test
    void coordinatorReadyMarkersShortCircuitAllThreeAdapters() {
        ProjectionNamespace namespace = new ProjectionNamespace(
                ORGANIZATION_ID,
                "copy-lock-characterization",
                "knowledge");
        ProjectionBatch contentBatch = singleKindBatch(
                namespace, "content-lock", ProjectionKind.CONTENT);
        ProjectionBatch lexicalBatch = singleKindBatch(
                namespace, "lexical-lock", ProjectionKind.LEXICAL);
        ProjectionBatch vectorBatch = singleKindBatch(
                namespace, "vector-lock", ProjectionKind.VECTOR);

        assertReadyShortCircuits(
                contentBatch,
                new OpenSearchCopyForwardCoordinator.CopyUnit(
                        ProjectionKind.CONTENT,
                        ProjectionKind.CONTENT.name(),
                        indexes.content()),
                () -> content.stageDelete(contentBatch, List.of()));
        assertReadyShortCircuits(
                lexicalBatch,
                new OpenSearchCopyForwardCoordinator.CopyUnit(
                        ProjectionKind.LEXICAL,
                        ProjectionKind.LEXICAL.name(),
                        indexes.lexical(lexicalBatch.id())),
                () -> lexical.stageDelete(lexicalBatch, List.of()));
        assertReadyShortCircuits(
                vectorBatch,
                new OpenSearchCopyForwardCoordinator.CopyUnit(
                        ProjectionKind.VECTOR,
                        ProjectionKind.VECTOR.name(),
                        indexes.vectorPattern()),
                () -> vectors.stageDelete(vectorBatch, List.of()));
    }

    @Test
    void canonicalMarkerKeysDistinguishGraphEntityAndRelationCopyUnits() {
        ProjectionNamespace namespace = new ProjectionNamespace(
                ORGANIZATION_ID,
                "copy-key-characterization",
                "knowledge");
        ProjectionBatch batch = graphOnlyBatch(namespace, "key", 0);
        String entityTarget = indexes.graphEntities(batch.id());
        String relationTarget = indexes.graphRelations(batch.id());
        String entityMarker = copyForward.markerId(
                batch,
                new OpenSearchCopyForwardCoordinator.CopyUnit(
                        ProjectionKind.GRAPH,
                        "GRAPH_ENTITY",
                        entityTarget));
        String relationMarker = copyForward.markerId(
                batch,
                new OpenSearchCopyForwardCoordinator.CopyUnit(
                        ProjectionKind.GRAPH,
                        "GRAPH_RELATION",
                        relationTarget));

        // challenge-verdict-codex.md requires exact canonical target identity
        // and distinct graph entity/relation copy units instead of hashCode keys.
        assertEquals(
                "copy:" + batch.id() + ":GRAPH_ENTITY:"
                        + entityTarget.length() + ":" + entityTarget,
                entityMarker);
        assertEquals(
                "copy:" + batch.id() + ":GRAPH_RELATION:"
                        + relationTarget.length() + ":" + relationTarget,
                relationMarker);
        assertFalse(entityMarker.equals(relationMarker));
    }

    @Test
    void losingPreparedBatchNeverLeaksAndCanBeDiscarded() {
        ProjectionNamespace namespace =
                new ProjectionNamespace(ORGANIZATION_ID, "competing-store", "knowledge");
        ProjectionBatch winner = contentOnlyBatch(namespace, "winner");
        ProjectionBatch loser = contentOnlyBatch(namespace, "loser");
        content.stageUpsert(winner, List.of(contentRecord("winner-record")));
        content.stageUpsert(loser, List.of(contentRecord("loser-record")));
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
        content.discard(loser, discardPermit(loser, NOW));
        QueryAssertions.assertNoBatchDocuments(
                operations,
                indexes.content(),
                loser.id());
    }

    private static ProjectionBatchLifecycle.Preparation preparationThatMustNotDiscard() {
        return new ProjectionBatchLifecycle.Preparation() {
            @Override
            public void prepare(ProjectionBatch batch) {
                // The receipt is the durable boundary under test.
            }

            @Override
            public ProjectionKind projectionKind() {
                return ProjectionKind.CONTENT;
            }

            @Override
            public void discard(ProjectionBatch batch, ProjectionDiscardPermit permit) {
                throw new AssertionError("ambiguous visible staging must not be discarded");
            }
        };
    }

    private static ProjectionBatchLifecycle.Preparation preparationThatMustNotRun() {
        return new ProjectionBatchLifecycle.Preparation() {
            @Override
            public void prepare(ProjectionBatch batch) {
                throw new AssertionError("a permitted committing attempt must not restage");
            }

            @Override
            public ProjectionKind projectionKind() {
                return ProjectionKind.CONTENT;
            }

            @Override
            public void discard(ProjectionBatch batch, ProjectionDiscardPermit permit) {
                throw new AssertionError("a permitted committing attempt must not discard");
            }
        };
    }

    private static final class CrashAfterHeadCreateOperations extends OpenSearchOperations {

        private boolean armed = true;

        private CrashAfterHeadCreateOperations(OpenSearchOperations delegate) {
            super(delegate.client(), delegate.bulkMaximumOperations());
        }

        @Override
        boolean create(String index, String id, Map<String, Object> document) {
            boolean created = super.create(index, id, document);
            if (created && armed && id.startsWith("head:")) {
                armed = false;
                throw new PublicationCrash();
            }
            return created;
        }
    }

    private static final class PublicationCrash extends Error {
    }

    @Test
    void processingStatusIsARebuildablePaginatedReadModel() {
        UUID firstRevision = id("status-first-revision");
        UUID secondRevision = id("status-second-revision");
        ProcessingStatusIndex.StatusRecord first =
                new ProcessingStatusIndex.StatusRecord(
                        ORGANIZATION_ID,
                        firstRevision,
                        id("status-first-job"),
                        ProcessingStatusIndex.State.PROCESSING,
                        "a".repeat(64),
                        null,
                        NOW,
                        Map.of("connector", "upload"));
        ProcessingStatusIndex.StatusRecord second =
                new ProcessingStatusIndex.StatusRecord(
                        ORGANIZATION_ID,
                        secondRevision,
                        id("status-second-job"),
                        ProcessingStatusIndex.State.FAILED,
                        "b".repeat(64),
                        "PARSER_UNAVAILABLE",
                        NOW.plusSeconds(1),
                        Map.of("connector", "slack"));
        statuses.upsert(first);
        statuses.upsert(second);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessingStatusIndex.StatusQuery(
                        Set.of(),
                        ProcessingStatusIndex.MAXIMUM_PAGE_SIZE + 1,
                        null));
        assertEquals(second, statuses.get(ORGANIZATION_ID, secondRevision).orElseThrow());
        ProcessingStatusIndex.StatusPage firstPage = statuses.search(
                ORGANIZATION_ID,
                new ProcessingStatusIndex.StatusQuery(Set.of(), 1, null));
        assertEquals(List.of(second), firstPage.records());
        ProcessingStatusIndex.StatusPage secondPage = statuses.search(
                ORGANIZATION_ID,
                new ProcessingStatusIndex.StatusQuery(
                        Set.of(),
                        1,
                        firstPage.nextCursor()));
        assertEquals(List.of(first), secondPage.records());
        assertNull(secondPage.nextCursor());

        statuses.delete(ORGANIZATION_ID, firstRevision);
        statuses.delete(ORGANIZATION_ID, firstRevision);
        assertTrue(statuses.get(ORGANIZATION_ID, firstRevision).isEmpty());
    }

    @Test
    void referenceTraversalAppliesAuthorizedEvidenceFilterAtEveryHop() {
        UUID allowedAsset = id("ppl-allowed-asset");
        UUID deniedAsset = id("ppl-denied-asset");
        UUID allowedRevision = id("ppl-allowed-revision");
        UUID deniedRevision = id("ppl-denied-revision");
        ProjectionNamespace namespace =
                new ProjectionNamespace(ORGANIZATION_ID, "ppl-acl", "knowledge");
        ProjectionBatch batch = new ProjectionBatch(
                id("ppl-acl-batch"),
                namespace,
                0,
                1,
                "ppl-acl",
                "manifest-ppl-acl",
                Set.of(ProjectionKind.GRAPH),
                NOW);
        graph.stageReplaceRevision(
                batch,
                graphRevision(
                        allowedAsset,
                        allowedRevision,
                        List.of(new CanonicalEntity(ENTITY_A_ID, "Employee")),
                        List.of()));
        graph.stageReplaceRevision(
                batch,
                graphRevision(
                        deniedAsset,
                        deniedRevision,
                        List.of(
                                new CanonicalEntity(ENTITY_A_ID, "Employee"),
                                new CanonicalEntity(ENTITY_B_ID, "Restricted Plan")),
                        List.of(new CanonicalRelation(
                                RELATION_ID,
                                ENTITY_A_ID,
                                ENTITY_B_ID,
                                RelationOrientation.DIRECTED))));
        publications.markPrepared(batch, ProjectionKind.GRAPH, NOW);
        ProjectionSnapshot snapshot = publications.publish(
                batch, commitPermit(batch, NOW), NOW);
        AuthorizedEvidenceScope scope = new AuthorizedEvidenceScope(
                ORGANIZATION_ID,
                ACTOR_ID,
                null,
                false,
                Set.of(allowedAsset),
                "model-v1",
                1,
                NOW);
        assertEquals(
                List.of(ENTITY_A_ID),
                traversal.expandEntityIds(
                        scope,
                        snapshot,
                        List.of(ENTITY_A_ID),
                        2,
                        10));
    }

    /**
     * The rest of this class builds adapters directly, which proves they work but
     * not that Spring produces them. Enabling the adapter has to contribute every
     * port it declares, because each one it claims is a port PostgreSQL no longer
     * gets to serve.
     */
    @Test
    void autoConfigurationContributesEveryPortItClaimsOnceEnabled() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(OpenSearchGraphRagAutoConfiguration.class))
                .withUserConfiguration(ObjectMapperConfiguration.class)
                .withPropertyValues(
                        "orgmemory.graph-rag.opensearch.enabled=true",
                        "orgmemory.graph-rag.opensearch.endpoint=http://%s:%d"
                                .formatted(
                                        opensearch.getHost(),
                                        opensearch.getMappedPort(9200)),
                        "orgmemory.graph-rag.opensearch.index-prefix=orgmemory-wiring-test")
                .run(context -> {
                    assertInstanceOf(
                            OpenSearchProjectionPublicationStore.class,
                            context.getBean(
                                    com.orgmemory.graphrag.storage.ProjectionPublicationStore
                                            .class));
                    assertInstanceOf(
                            OpenSearchContentStore.class, context.getBean(ContentStore.class));
                    assertInstanceOf(
                            OpenSearchGraphStore.class, context.getBean(GraphStore.class));
                    assertInstanceOf(
                            OpenSearchLexicalIndex.class, context.getBean(LexicalIndex.class));
                    assertInstanceOf(
                            OpenSearchVectorIndex.class, context.getBean(VectorIndex.class));
                    assertInstanceOf(
                            OpenSearchProcessingStatusIndex.class,
                            context.getBean(ProcessingStatusIndex.class));
                });
    }

    /** The collaborator an application already owns before this adapter loads. */
    @Configuration(proxyBeanMethods = false)
    static class ObjectMapperConfiguration {

        @Bean
        ObjectMapper wiringTestObjectMapper() {
            return new ObjectMapper();
        }
    }

    private static ProjectionBatch batch(
            ProjectionNamespace namespace,
            String key,
            long previousGeneration) {
        return new ProjectionBatch(
                id(namespace.workspace() + "-" + key),
                namespace,
                previousGeneration,
                previousGeneration + 1,
                "idempotency-" + key,
                "manifest-" + key,
                Set.of(ProjectionKind.CONTENT),
                NOW.plusSeconds(previousGeneration));
    }

    private static ProjectionBatch sharedBatch(String key, long previous) {
        return new ProjectionBatch(
                id("opensearch-shared-batch-" + key),
                NAMESPACE,
                previous,
                previous + 1,
                "opensearch-shared-" + key,
                "manifest-shared-" + key,
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
                id("opensearch-competing-" + key),
                namespace,
                0,
                1,
                "opensearch-competing-" + key,
                "manifest-competing-" + key,
                Set.of(ProjectionKind.CONTENT),
                NOW);
    }

    private static ProjectionBatch graphOnlyBatch(
            ProjectionNamespace namespace,
            String key,
            long previousGeneration) {
        return new ProjectionBatch(
                id(namespace.workspace() + "-graph-" + key),
                namespace,
                previousGeneration,
                previousGeneration + 1,
                namespace.workspace() + "-graph-" + key,
                "manifest-" + namespace.workspace() + "-graph-" + key,
                Set.of(ProjectionKind.GRAPH),
                NOW.plusSeconds(previousGeneration));
    }

    private static ProjectionBatch copyBatch(
            ProjectionNamespace namespace,
            String key,
            long previousGeneration) {
        return new ProjectionBatch(
                id(namespace.workspace() + "-copy-" + key),
                namespace,
                previousGeneration,
                previousGeneration + 1,
                namespace.workspace() + "-copy-" + key,
                "manifest-" + namespace.workspace() + "-copy-" + key,
                Set.of(
                        ProjectionKind.CONTENT,
                        ProjectionKind.LEXICAL,
                        ProjectionKind.VECTOR),
                NOW.plusSeconds(previousGeneration));
    }

    private static ProjectionBatch singleKindBatch(
            ProjectionNamespace namespace,
            String key,
            ProjectionKind kind) {
        return new ProjectionBatch(
                id(namespace.workspace() + "-" + key),
                namespace,
                0,
                1,
                namespace.workspace() + "-" + key,
                "manifest-" + namespace.workspace() + "-" + key,
                Set.of(kind),
                NOW);
    }

    private static void assertCopiedSource(
            String sourceIndex,
            String sourceId,
            String targetIndex,
            String targetId,
            ProjectionBatch targetBatch) throws Exception {
        OpenSearchOperations.VersionedDocument source =
                operations.get(sourceIndex, sourceId);
        OpenSearchOperations.VersionedDocument target =
                operations.get(targetIndex, targetId);
        assertTrue(source != null, "source document should exist");
        assertTrue(target != null, "copied document should exist");

        Map<String, Object> expected = new LinkedHashMap<>(source.source());
        expected.put(OpenSearchProjectionCodec.BATCH_ID, targetBatch.id().toString());
        expected.put(OpenSearchProjectionCodec.GENERATION, targetBatch.generation());
        ObjectMapper canonical = new ObjectMapper()
                .configure(
                        com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,
                        true);
        assertArrayEquals(
                canonical.writeValueAsBytes(expected),
                canonical.writeValueAsBytes(target.source()));
    }

    private static void assertReadyShortCircuits(
            ProjectionBatch batch,
            OpenSearchCopyForwardCoordinator.CopyUnit unit,
            Runnable copyAttempt) {
        String markerId = copyForward.markerId(batch, unit);
        try {
            Map<String, Object> ready = OpenSearchProjectionCodec.batch(batch, "PREPARING");
            ready.put("document_kind", "COPY_FORWARD");
            ready.put("projection_kind", unit.projectionKind().name());
            ready.put("copy_unit", unit.logicalUnit());
            ready.put("target_index", unit.targetIdentity());
            ready.put("copy_status", "READY");
            ready.put("copy_owner", "characterization-owner");
            ready.put("copy_attempt", 1);
            ready.put("copy_started_at", NOW.toString());
            ready.put("copy_completed_at", NOW.plusSeconds(1).toString());
            assertTrue(operations.create(indexes.control(), markerId, ready));
            copyAttempt.run();
            OpenSearchOperations.VersionedDocument observed =
                    operations.get(indexes.control(), markerId);
            assertEquals("characterization-owner", observed.source().get("copy_owner"));
        } finally {
            operations.deleteIfExists(indexes.control(), markerId);
        }
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
                id("opensearch-entity-contribution-a"),
                entityA,
                "PERSON",
                "Employee",
                provenance);
        EntityContribution contributionB = new EntityContribution(
                id("opensearch-entity-contribution-b"),
                entityB,
                "POLICY",
                "Probation policy",
                provenance);
        RelationContribution relation = new RelationContribution(
                id("opensearch-relation-contribution"),
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

    private static GraphRevisionContributions graphRevision(
            UUID assetId,
            UUID revisionId,
            List<CanonicalEntity> canonicalEntities,
            List<CanonicalRelation> canonicalRelations) {
        EvidenceReference evidence = new EvidenceReference(
                ORGANIZATION_ID,
                assetId,
                revisionId,
                id(revisionId + "-chunk"),
                id(revisionId + "-acl"),
                1);
        EvidenceProvenance provenance = new EvidenceProvenance(
                evidence,
                1,
                "test",
                "test-model",
                "test-prompt",
                1,
                NOW);
        List<EntityContribution> entityContributions = canonicalEntities.stream()
                .map(entity -> new EntityContribution(
                        id(revisionId + "-entity-" + entity.id()),
                        entity,
                        "ENTITY",
                        entity.normalizedName(),
                        provenance))
                .toList();
        List<RelationContribution> relationContributions = canonicalRelations.stream()
                .map(relation -> new RelationContribution(
                        id(revisionId + "-relation-" + relation.id()),
                        relation,
                        "RELATED_TO",
                        List.of("related"),
                        "Related evidence",
                        1,
                        provenance))
                .toList();
        return new GraphRevisionContributions(
                ORGANIZATION_ID,
                assetId,
                revisionId,
                1,
                entityContributions,
                relationContributions);
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

    private static ContentStore.ContentRecord contentRecord(String recordId) {
        return new ContentStore.ContentRecord(
                recordId,
                new EvidenceReference(
                        ORGANIZATION_ID,
                        ASSET_ID,
                        id(recordId + "-revision"),
                        id(recordId + "-chunk"),
                        id(recordId + "-acl"),
                        1),
                ContentStore.ContentKind.CHUNK,
                recordId,
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
