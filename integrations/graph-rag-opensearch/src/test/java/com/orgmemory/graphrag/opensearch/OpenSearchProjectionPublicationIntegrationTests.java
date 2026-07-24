package com.orgmemory.graphrag.opensearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.LexicalIndex;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore.PublicationConflictException;
import com.orgmemory.graphrag.storage.ProcessingStatusIndex;
import com.orgmemory.graphrag.storage.VectorIndex;
import com.orgmemory.graphrag.testkit.ProjectionPublicationConformance;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
    private static OpenSearchIndexNames indexes;
    private static OpenSearchContentStore content;
    private static OpenSearchLexicalIndex lexical;
    private static OpenSearchVectorIndex vectors;
    private static OpenSearchGraphStore graph;
    private static OpenSearchPplGraphLookup ppl;
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
        content = new OpenSearchContentStore(operations, publications, indexes);
        lexical = new OpenSearchLexicalIndex(operations, publications, indexes);
        vectors = new OpenSearchVectorIndex(operations, publications, indexes);
        ppl = new OpenSearchPplGraphLookup(
                operations,
                indexes,
                new ObjectMapper(),
                properties.isPplGraphLookupEnabled());
        graph = new OpenSearchGraphStore(
                operations,
                publications,
                indexes,
                properties.getGraphMaximumFrontier(),
                ppl);
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
    void previousHeadIsBackfilledBeforeTheNextGenerationBecomesCurrent() {
        ProjectionNamespace namespace = new ProjectionNamespace(
                id("history-organization"),
                "default",
                "knowledge");
        ProjectionBatch first = batch(namespace, "first", 0);
        publications.markPrepared(first, ProjectionKind.CONTENT, NOW);
        var firstSnapshot = publications.publish(first, NOW);

        ProjectionBatch second = batch(namespace, "second", 1);
        publications.markPrepared(second, ProjectionKind.CONTENT, NOW.plusSeconds(1));
        var secondSnapshot = publications.publish(second, NOW.plusSeconds(1));

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

        var snapshot = publications.publish(winner, NOW);
        assertThrows(
                PublicationConflictException.class,
                () -> publications.publish(loser, NOW.plusSeconds(1)));
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
                graph.expandEntityIds(
                        allowed,
                        firstSnapshot,
                        List.of(ENTITY_A_ID),
                        1,
                        10));
        assertTrue(
                ppl.successfulExecutions() > 0,
                "the standard OpenSearch image should exercise PPL graphLookup");

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
                publications.publish(second, NOW.plusSeconds(1));

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
                graph.expandEntityIds(
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
        publications.publish(first, NOW);

        ProjectionBatch second = graphOnlyBatch(namespace, "second", 1);
        graph.stageReplaceRevision(
                second,
                graphRevision(
                        ASSET_ID,
                        changedRevision,
                        List.of(new CanonicalEntity(ENTITY_B_ID, "Replacement entity")),
                        List.of()));
        publications.markPrepared(second, ProjectionKind.GRAPH, NOW.plusSeconds(1));
        ProjectionSnapshot snapshot = publications.publish(second, NOW.plusSeconds(1));

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
    void losingPreparedBatchNeverLeaksAndCanBeDiscarded() {
        ProjectionNamespace namespace =
                new ProjectionNamespace(ORGANIZATION_ID, "competing-store", "knowledge");
        ProjectionBatch winner = contentOnlyBatch(namespace, "winner");
        ProjectionBatch loser = contentOnlyBatch(namespace, "loser");
        content.stageUpsert(winner, List.of(contentRecord("winner-record")));
        content.stageUpsert(loser, List.of(contentRecord("loser-record")));
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
        content.discard(loser);
        QueryAssertions.assertNoBatchDocuments(
                operations,
                indexes.content(),
                loser.id());
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
    void pplAppliesAuthorizedEvidenceFilterAtEveryTraversalHop() {
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
        ProjectionSnapshot snapshot = publications.publish(batch, NOW);
        AuthorizedEvidenceScope scope = new AuthorizedEvidenceScope(
                ORGANIZATION_ID,
                ACTOR_ID,
                null,
                false,
                Set.of(allowedAsset),
                "model-v1",
                1,
                NOW);
        long executionsBefore = ppl.successfulExecutions();

        assertEquals(
                List.of(ENTITY_A_ID),
                graph.expandEntityIds(
                        scope,
                        snapshot,
                        List.of(ENTITY_A_ID),
                        2,
                        10));
        assertTrue(ppl.successfulExecutions() > executionsBefore);
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
