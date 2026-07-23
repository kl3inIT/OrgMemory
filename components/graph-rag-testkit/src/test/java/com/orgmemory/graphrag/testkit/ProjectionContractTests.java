package com.orgmemory.graphrag.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore.PublicationConflictException;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore.PublicationNotReadyException;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectionContractTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ALLOWED_ASSET_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID DENIED_ASSET_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");
    private static final ProjectionNamespace NAMESPACE =
            new ProjectionNamespace(ORGANIZATION_ID, "default", "knowledge");

    @Test
    void publicationRequiresEveryProjectionAndUsesCompareAndSet() {
        InMemoryProjectionPublicationStore publications =
                new InMemoryProjectionPublicationStore();
        ProjectionBatch first = batch(
                "first",
                0,
                1,
                Set.of(ProjectionKind.CONTENT, ProjectionKind.VECTOR));

        assertThrows(
                PublicationNotReadyException.class,
                () -> publications.publish(first, NOW));
        first.requiredProjections()
                .forEach(kind -> publications.markPrepared(first, kind, NOW));

        ProjectionSnapshot published = publications.publish(first, NOW);
        assertEquals(1, published.generation());
        assertEquals(
                published,
                publications.publish(first, NOW.plusSeconds(5)));

        ProjectionBatch stale = batch("stale", 0, 1, Set.of(ProjectionKind.CONTENT));
        publications.markPrepared(stale, ProjectionKind.CONTENT, NOW);
        assertThrows(
                PublicationConflictException.class,
                () -> publications.publish(stale, NOW));
    }

    @Test
    void publicationReferenceAdapterPassesTheReusableConformanceSuite() {
        ProjectionPublicationConformance.verify(
                InMemoryProjectionPublicationStore::new);
    }

    @Test
    void publicationManifestFingerprintMustBePresent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProjectionBatch(
                        UUID.randomUUID(),
                        NAMESPACE,
                        0,
                        1,
                        "publication",
                        " ",
                        Set.of(ProjectionKind.CONTENT),
                        NOW));
        assertThrows(
                NullPointerException.class,
                () -> new ProjectionBatch(
                        UUID.randomUUID(),
                        NAMESPACE,
                        0,
                        1,
                        "publication",
                        null,
                        Set.of(ProjectionKind.CONTENT),
                        NOW));
    }

    @Test
    void publicationGenerationCannotOverflow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProjectionBatch(
                        UUID.randomUUID(),
                        NAMESPACE,
                        Long.MAX_VALUE,
                        Long.MIN_VALUE,
                        "publication",
                        "manifest",
                        Set.of(ProjectionKind.CONTENT),
                        NOW));
    }

    @Test
    void abortedBatchCannotBecomeVisible() {
        InMemoryProjectionPublicationStore publications =
                new InMemoryProjectionPublicationStore();
        ProjectionBatch batch = batch("aborted", 0, 1, Set.of(ProjectionKind.CONTENT));

        publications.abort(batch, "fixture failure", NOW);

        assertThrows(
                PublicationConflictException.class,
                () -> publications.publish(batch, NOW));
        assertTrue(publications.current(NAMESPACE).isEmpty());
    }

    @Test
    void contentReadsUseThePublishedSnapshotAndResolvedEvidenceScope() {
        InMemoryProjectionPublicationStore publications =
                new InMemoryProjectionPublicationStore();
        InMemoryContentStore content = new InMemoryContentStore(publications);
        ProjectionBatch batch = batch("content", 0, 1, Set.of(ProjectionKind.CONTENT));
        content.stageUpsert(
                batch,
                List.of(
                        record("allowed", ALLOWED_ASSET_ID),
                        record("denied", DENIED_ASSET_ID)));
        publications.markPrepared(batch, ProjectionKind.CONTENT, NOW);
        ProjectionSnapshot snapshot = publications.publish(batch, NOW);

        assertTrue(content.get(scope(Set.of(ALLOWED_ASSET_ID), 7), snapshot, "allowed")
                .isPresent());
        assertTrue(content.get(scope(Set.of(ALLOWED_ASSET_ID), 7), snapshot, "denied")
                .isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> content.get(
                        scope(Set.of(ALLOWED_ASSET_ID), 7),
                        new ProjectionSnapshot(
                                snapshot.batchId(),
                                new ProjectionNamespace(
                                        UUID.randomUUID(), "default", "knowledge"),
                                snapshot.generation(),
                                snapshot.manifestFingerprint(),
                                snapshot.projections(),
                                snapshot.publishedAt()),
                        "allowed"));
        assertThrows(
                IllegalStateException.class,
                () -> content.get(
                        scope(Set.of(ALLOWED_ASSET_ID), 7),
                        new ProjectionSnapshot(
                                snapshot.batchId(),
                                snapshot.namespace(),
                                snapshot.generation(),
                                "fabricated-manifest",
                                snapshot.projections(),
                                snapshot.publishedAt()),
                        "allowed"));
    }

    @Test
    void contentGenerationInheritsOnlyFromItsPublishedPredecessor() {
        InMemoryProjectionPublicationStore publications =
                new InMemoryProjectionPublicationStore();
        InMemoryContentStore content = new InMemoryContentStore(publications);

        ProjectionBatch aborted =
                batch("aborted-content", 0, 1, Set.of(ProjectionKind.CONTENT));
        content.stageUpsert(aborted, List.of(record("aborted", ALLOWED_ASSET_ID)));
        publications.abort(aborted, "fixture failure", NOW);

        ProjectionBatch first =
                batch("published-content", 0, 1, Set.of(ProjectionKind.CONTENT));
        content.stageUpsert(first, List.of(record("published", ALLOWED_ASSET_ID)));
        publications.markPrepared(first, ProjectionKind.CONTENT, NOW);
        publications.publish(first, NOW);

        ProjectionBatch second =
                batch("second-content", 1, 2, Set.of(ProjectionKind.CONTENT));
        content.stageUpsert(second, List.of(record("second", ALLOWED_ASSET_ID)));
        publications.markPrepared(second, ProjectionKind.CONTENT, NOW.plusSeconds(1));
        ProjectionSnapshot secondSnapshot =
                publications.publish(second, NOW.plusSeconds(1));

        AuthorizedEvidenceScope scope = scope(Set.of(ALLOWED_ASSET_ID), 7);
        assertTrue(content.get(scope, secondSnapshot, "aborted").isEmpty());
        assertTrue(content.get(scope, secondSnapshot, "published").isPresent());
        assertTrue(content.get(scope, secondSnapshot, "second").isPresent());
    }

    @Test
    void retrievalCacheKeyChangesWithAclOrProjectionGeneration() {
        InMemoryRetrievalResultCache cache = new InMemoryRetrievalResultCache();
        ProjectionSnapshot generationOne = new ProjectionSnapshot(
                UUID.randomUUID(),
                NAMESPACE,
                1,
                "manifest-generation-1",
                Set.of(ProjectionKind.CONTENT),
                NOW);
        ProjectionSnapshot generationTwo = new ProjectionSnapshot(
                UUID.randomUUID(),
                NAMESPACE,
                2,
                "manifest-generation-2",
                Set.of(ProjectionKind.CONTENT),
                NOW.plusSeconds(1));

        RetrievalResultCache.Key baseline = RetrievalResultCache.key(
                scope(Set.of(ALLOWED_ASSET_ID), 7),
                generationOne,
                "query-hash",
                "SECURE_MIX",
                "route-v1");
        RetrievalResultCache.Key aclChanged = RetrievalResultCache.key(
                scope(Set.of(ALLOWED_ASSET_ID), 8),
                generationOne,
                "query-hash",
                "SECURE_MIX",
                "route-v1");
        RetrievalResultCache.Key projectionChanged = RetrievalResultCache.key(
                scope(Set.of(ALLOWED_ASSET_ID), 7),
                generationTwo,
                "query-hash",
                "SECURE_MIX",
                "route-v1");

        assertNotEquals(baseline, aclChanged);
        assertNotEquals(baseline, projectionChanged);

        RetrievalResultCache.Entry entry = new RetrievalResultCache.Entry(
                "application/json",
                "{}",
                List.of(record("allowed", ALLOWED_ASSET_ID).evidence()),
                NOW,
                NOW.plusSeconds(30));
        cache.put(baseline, entry);
        assertEquals(entry, cache.get(baseline, NOW).orElseThrow());
        assertTrue(cache.get(aclChanged, NOW).isEmpty());
        assertTrue(cache.get(projectionChanged, NOW).isEmpty());
    }

    @Test
    void modelInvocationCacheIsExactTenantScopedAndExpires() {
        InMemoryModelInvocationCache cache = new InMemoryModelInvocationCache();
        ModelInvocationCache.Key key = new ModelInvocationCache.Key(
                NAMESPACE,
                "KEYWORD_EXTRACTION",
                "input-hash",
                "route-v1",
                "profile-v1");
        ModelInvocationCache.Entry entry = new ModelInvocationCache.Entry(
                "application/json",
                "{\"keywords\":[]}",
                NOW,
                NOW.plusSeconds(30));

        cache.put(key, entry);

        assertEquals(entry, cache.get(key, NOW.plusSeconds(29)).orElseThrow());
        assertTrue(cache.get(key, NOW.plusSeconds(30)).isEmpty());
    }

    @Test
    void authorizationFingerprintIsIndependentOfSetIterationOrder() {
        AuthorizedEvidenceScope first =
                scope(Set.of(ALLOWED_ASSET_ID, DENIED_ASSET_ID), 7);
        AuthorizedEvidenceScope second =
                scope(Set.of(DENIED_ASSET_ID, ALLOWED_ASSET_ID), 7);

        assertEquals(
                first.authorizationFingerprint(),
                second.authorizationFingerprint());
    }

    @Test
    void primitiveVectorDefensivelyCopiesItsValues() {
        float[] values = {0.1f, 0.2f, 0.3f};
        FloatVector vector = new FloatVector(values);
        values[0] = 99.0f;
        float[] exposed = vector.copyValues();
        exposed[1] = 99.0f;

        assertEquals(0.1f, vector.valueAt(0));
        assertEquals(0.2f, vector.valueAt(1));
    }

    private static ProjectionBatch batch(
            String key,
            long previous,
            long generation,
            Set<ProjectionKind> projections) {
        return new ProjectionBatch(
                UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                NAMESPACE,
                previous,
                generation,
                key,
                "manifest-" + key,
                projections,
                NOW);
    }

    private static AuthorizedEvidenceScope scope(Set<UUID> assets, long aclGeneration) {
        return new AuthorizedEvidenceScope(
                ORGANIZATION_ID,
                ACTOR_ID,
                null,
                false,
                assets,
                "model-v1",
                aclGeneration,
                NOW);
    }

    private static ContentStore.ContentRecord record(String id, UUID assetId) {
        return new ContentStore.ContentRecord(
                id,
                new EvidenceReference(
                        ORGANIZATION_ID,
                        assetId,
                        UUID.nameUUIDFromBytes((id + "-revision").getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)),
                        UUID.nameUUIDFromBytes((id + "-chunk").getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)),
                        UUID.nameUUIDFromBytes((id + "-acl").getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)),
                        7),
                ContentStore.ContentKind.CHUNK,
                id + " content",
                2,
                java.util.Map.of());
    }
}
