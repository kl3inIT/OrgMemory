package com.orgmemory.graphrag.opensearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;

class OpenSearchCopyForwardCoordinatorTests {

    private static final Instant NOW = Instant.parse("2026-08-01T08:00:00Z");
    private static final ProjectionNamespace NAMESPACE = new ProjectionNamespace(
            id("copy-forward-organization"),
            "default",
            "knowledge");

    @Test
    void independentCoordinatorCannotStealLiveCopyingMarker() throws Exception {
        FakeMarkerStore markers = new FakeMarkerStore();
        OpenSearchCopyForwardCoordinator first = coordinator(markers, "owner-one");
        OpenSearchCopyForwardCoordinator second = coordinator(markers, "owner-two");
        ProjectionBatch batch = batch("split-process");
        OpenSearchCopyForwardCoordinator.CopyUnit unit = unit("content-index");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean secondCopied = new AtomicBoolean();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> active = executor.submit(() -> first.copyForward(
                    batch,
                    unit,
                    () -> {
                        entered.countDown();
                        await(release);
                    },
                    () -> {}));
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            OpenSearchProjectionException conflict = assertThrows(
                    OpenSearchProjectionException.class,
                    () -> second.copyForward(
                            batch,
                            unit,
                            () -> secondCopied.set(true),
                            () -> {}));

            assertTrue(conflict.getMessage().contains("already in progress"));
            assertFalse(secondCopied.get());
            assertEquals("owner-one", markers.source(first.markerId(batch, unit)).get("copy_owner"));
            assertEquals(OpenSearchCopyForwardCoordinator.COPYING,
                    markers.source(first.markerId(batch, unit)).get("copy_status"));

            release.countDown();
            active.get(5, TimeUnit.SECONDS);
            assertEquals(OpenSearchCopyForwardCoordinator.READY,
                    markers.source(first.markerId(batch, unit)).get("copy_status"));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedPartialCopyIsCleanedAndExplicitFailedStateEnablesRetry() {
        FakeMarkerStore markers = new FakeMarkerStore();
        OpenSearchCopyForwardCoordinator first = coordinator(markers, "owner-one");
        OpenSearchCopyForwardCoordinator second = coordinator(markers, "owner-two");
        ProjectionBatch batch = batch("failed-retry");
        OpenSearchCopyForwardCoordinator.CopyUnit unit = unit("vector-pattern");
        List<String> partialOutput = new ArrayList<>();
        AtomicInteger cleanups = new AtomicInteger();

        assertThrows(
                IllegalStateException.class,
                () -> first.copyForward(
                        batch,
                        unit,
                        () -> {
                            partialOutput.add("first-page");
                            throw new IllegalStateException("bulk page failed");
                        },
                        () -> {
                            cleanups.incrementAndGet();
                            partialOutput.clear();
                        }));

        String markerId = first.markerId(batch, unit);
        assertTrue(partialOutput.isEmpty());
        assertEquals(OpenSearchCopyForwardCoordinator.FAILED,
                markers.source(markerId).get("copy_status"));
        assertEquals(1L, ((Number) markers.source(markerId).get("copy_attempt")).longValue());

        second.copyForward(
                batch,
                unit,
                () -> partialOutput.add("complete-copy"),
                () -> {
                    cleanups.incrementAndGet();
                    partialOutput.clear();
                });

        assertEquals(List.of("complete-copy"), partialOutput);
        assertEquals(2, cleanups.get());
        assertEquals(OpenSearchCopyForwardCoordinator.READY,
                markers.source(markerId).get("copy_status"));
        assertEquals(2L, ((Number) markers.source(markerId).get("copy_attempt")).longValue());
        assertEquals("owner-two", markers.source(markerId).get("copy_owner"));
    }

    @Test
    void crashStaleCopyingRemainsPoisonedAndIsNeverTakenOver() {
        FakeMarkerStore markers = new FakeMarkerStore();
        OpenSearchCopyForwardCoordinator coordinator = coordinator(markers, "new-owner");
        ProjectionBatch batch = batch("crash-stale");
        OpenSearchCopyForwardCoordinator.CopyUnit unit = unit("content-index");
        String markerId = coordinator.markerId(batch, unit);
        Map<String, Object> copying = marker(batch, unit, "crashed-owner", 3);
        assertTrue(markers.create(markerId, copying));
        AtomicBoolean copied = new AtomicBoolean();
        AtomicBoolean cleaned = new AtomicBoolean();

        assertThrows(
                OpenSearchProjectionException.class,
                () -> coordinator.copyForward(
                        batch,
                        unit,
                        () -> copied.set(true),
                        () -> cleaned.set(true)));

        assertFalse(copied.get());
        assertFalse(cleaned.get());
        assertEquals("crashed-owner", markers.source(markerId).get("copy_owner"));
        assertEquals(3L, ((Number) markers.source(markerId).get("copy_attempt")).longValue());
        assertEquals(OpenSearchCopyForwardCoordinator.COPYING,
                markers.source(markerId).get("copy_status"));
    }

    @Test
    void sameJvmWaiterRetriesAfterRetiredLockAndObservesReady() throws Exception {
        FakeMarkerStore markers = new FakeMarkerStore();
        OpenSearchCopyForwardCoordinator coordinator = coordinator(markers, "same-jvm-owner");
        ProjectionBatch batch = batch("retired-lock");
        OpenSearchCopyForwardCoordinator.CopyUnit unit = unit("lexical-index");
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicBoolean secondCopied = new AtomicBoolean();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> coordinator.copyForward(
                    batch,
                    unit,
                    () -> {
                        firstEntered.countDown();
                        await(releaseFirst);
                    },
                    () -> {}));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> {
                secondStarted.countDown();
                coordinator.copyForward(
                        batch,
                        unit,
                        () -> secondCopied.set(true),
                        () -> {});
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertFalse(second.isDone());

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertFalse(secondCopied.get());
            assertEquals(0, coordinator.localLockCount());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void pageToBulkStreamingHonorsOperationAndEncodedByteBounds() {
        FakeMarkerStore markers = new FakeMarkerStore();
        List<List<BulkOperation>> bulks = new ArrayList<>();
        List<Integer> observedPageSizes = new ArrayList<>();
        OpenSearchCopyForwardCoordinator.PageScanner pages =
                (index, query, limit, pageSize, consumer) -> {
                    observedPageSizes.add(pageSize);
                    List<OpenSearchScanner.StoredHit> hits = new ArrayList<>();
                    for (int number = 0; number < 7; number++) {
                        hits.add(new OpenSearchScanner.StoredHit(
                                index,
                                "id-" + number,
                                Map.of("record_id", "record-" + number)));
                    }
                    consumer.accept(List.copyOf(hits.subList(0, pageSize)));
                    consumer.accept(List.copyOf(hits.subList(pageSize, 6)));
                    consumer.accept(List.copyOf(hits.subList(6, 7)));
                };
        OpenSearchCopyForwardCoordinator coordinator = new OpenSearchCopyForwardCoordinator(
                markers,
                pages,
                operations -> bulks.add(List.copyOf(operations)),
                3,
                100,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "stream-owner",
                new ConcurrentHashMap<>(),
                new ObjectMapper());

        coordinator.stream(
                "source-index",
                Query.of(query -> query.matchAll(matchAll -> matchAll)),
                hit -> new OpenSearchCopyForwardCoordinator.CopyOperation(
                        BulkOperation.of(operation -> operation.index(index -> index
                                .index("target-index")
                                .id(hit.id())
                                .document(hit.source()))),
                        40));

        assertEquals(List.of(3), observedPageSizes);
        assertEquals(List.of(2, 2, 2, 1), bulks.stream().map(List::size).toList());
        assertEquals(7, bulks.stream().mapToInt(List::size).sum());
    }

    @Test
    void canonicalTargetIdentityDoesNotCollapseJavaHashCollisionsOrGraphUnits() {
        assertEquals("Aa".hashCode(), "BB".hashCode());
        FakeMarkerStore markers = new FakeMarkerStore();
        OpenSearchCopyForwardCoordinator coordinator = coordinator(markers, "identity-owner");
        ProjectionBatch batch = batch("identity");
        String first = coordinator.markerId(batch, unit("Aa"));
        String second = coordinator.markerId(batch, unit("BB"));
        String entity = coordinator.markerId(
                batch,
                new OpenSearchCopyForwardCoordinator.CopyUnit(
                        ProjectionKind.GRAPH, "GRAPH_ENTITY", "graph-target"));
        String relation = coordinator.markerId(
                batch,
                new OpenSearchCopyForwardCoordinator.CopyUnit(
                        ProjectionKind.GRAPH, "GRAPH_RELATION", "graph-target"));

        assertNotEquals(first, second);
        assertTrue(first.endsWith(":2:Aa"));
        assertTrue(second.endsWith(":2:BB"));
        assertNotEquals(entity, relation);
    }

    private static OpenSearchCopyForwardCoordinator coordinator(
            FakeMarkerStore markers,
            String owner) {
        return new OpenSearchCopyForwardCoordinator(
                markers,
                (index, query, limit, pageSize, consumer) -> {},
                operations -> {},
                10,
                1_000,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> owner,
                new ConcurrentHashMap<String, ReentrantLock>(),
                new ObjectMapper());
    }

    private static OpenSearchCopyForwardCoordinator.CopyUnit unit(String target) {
        return new OpenSearchCopyForwardCoordinator.CopyUnit(
                ProjectionKind.CONTENT,
                ProjectionKind.CONTENT.name(),
                target);
    }

    private static ProjectionBatch batch(String key) {
        return new ProjectionBatch(
                id("batch-" + key),
                NAMESPACE,
                0,
                1,
                "idempotency-" + key,
                "manifest-" + key,
                Set.of(ProjectionKind.CONTENT),
                NOW);
    }

    private static Map<String, Object> marker(
            ProjectionBatch batch,
            OpenSearchCopyForwardCoordinator.CopyUnit unit,
            String owner,
            long attempt) {
        Map<String, Object> marker = OpenSearchProjectionCodec.batch(batch, "PREPARING");
        marker.put("document_kind", "COPY_FORWARD");
        marker.put("projection_kind", unit.projectionKind().name());
        marker.put("copy_unit", unit.logicalUnit());
        marker.put("target_index", unit.targetIdentity());
        marker.put("copy_status", OpenSearchCopyForwardCoordinator.COPYING);
        marker.put("copy_owner", owner);
        marker.put("copy_attempt", attempt);
        marker.put("copy_started_at", NOW.toString());
        return marker;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while awaiting test latch", interrupted);
        }
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class FakeMarkerStore
            implements OpenSearchCopyForwardCoordinator.MarkerStore {

        private final Map<String, Stored> documents = new LinkedHashMap<>();

        @Override
        public synchronized OpenSearchOperations.VersionedDocument get(String markerId) {
            Stored stored = documents.get(markerId);
            return stored == null
                    ? null
                    : new OpenSearchOperations.VersionedDocument(
                            stored.source(), stored.sequenceNumber(), 1);
        }

        @Override
        public synchronized boolean create(
                String markerId,
                Map<String, Object> document) {
            if (documents.containsKey(markerId)) {
                return false;
            }
            documents.put(markerId, new Stored(Map.copyOf(document), 0));
            return true;
        }

        @Override
        public synchronized boolean compareAndSet(
                String markerId,
                OpenSearchOperations.VersionedDocument expected,
                Map<String, Object> document) {
            Stored current = documents.get(markerId);
            if (current == null
                    || current.sequenceNumber() != expected.sequenceNumber()
                    || expected.primaryTerm() != 1) {
                return false;
            }
            documents.put(
                    markerId,
                    new Stored(Map.copyOf(document), current.sequenceNumber() + 1));
            return true;
        }

        synchronized Map<String, Object> source(String markerId) {
            return documents.get(markerId).source();
        }

        private record Stored(Map<String, Object> source, long sequenceNumber) {
        }
    }
}
