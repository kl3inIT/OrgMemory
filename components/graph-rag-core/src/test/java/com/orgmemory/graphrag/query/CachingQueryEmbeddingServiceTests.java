package com.orgmemory.graphrag.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.chunking.TextEmbeddingPort;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CachingQueryEmbeddingServiceTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");
    private static final ProjectionNamespace NAMESPACE =
            new ProjectionNamespace(ORGANIZATION_ID, "main", "knowledge");

    @Test
    void exactSecondRequestReusesPersistedEmbeddingWithoutCallingProvider() {
        RecordingEmbeddingPort provider = new RecordingEmbeddingPort("1", 2);
        CachingQueryEmbeddingService service = service(provider, new MapModelInvocationCache());

        List<FloatVector> first = service.embedAll(
                NAMESPACE, PROFILE_ID, 2, List.of("Leave policy"));
        List<FloatVector> second = service.embedAll(
                NAMESPACE, PROFILE_ID, 2, List.of("Leave policy"));

        assertEquals(first, second);
        assertEquals(1, provider.calls());
    }

    @Test
    void deduplicatesInputsAndBatchesOnlyCacheMisses() {
        MapModelInvocationCache cache = new MapModelInvocationCache();
        RecordingEmbeddingPort provider = new RecordingEmbeddingPort("1", 2);
        CachingQueryEmbeddingService service = service(provider, cache);
        service.embedAll(NAMESPACE, PROFILE_ID, 2, List.of("cached"));

        List<FloatVector> result = service.embedAll(
                NAMESPACE,
                PROFILE_ID,
                2,
                List.of("cached", "new one", "new two", "new one"));

        assertEquals(4, result.size());
        assertEquals(result.get(1), result.get(3));
        assertEquals(2, provider.calls());
        assertEquals(List.of(List.of("cached"), List.of("new one", "new two")), provider.batches());
    }

    @Test
    void overlappingMissesCoalesceWhenPersistenceIsUnavailable() throws Exception {
        MissRegistrationRaceCache cache = new MissRegistrationRaceCache();
        RecordingEmbeddingPort provider = new RecordingEmbeddingPort("1", 2);
        LatchingEventSink events = new LatchingEventSink();
        CachingQueryEmbeddingService service = new CachingQueryEmbeddingService(
                provider,
                cache,
                Duration.ofDays(7),
                Clock.fixed(NOW, ZoneOffset.UTC),
                events);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<FloatVector>> first = executor.submit(() -> service.embedAll(
                    NAMESPACE, PROFILE_ID, 2, List.of("Leave policy")));
            assertTrue(cache.awaitFirstMissObserved());

            Future<List<FloatVector>> second = executor.submit(() -> service.embedAll(
                    NAMESPACE, PROFILE_ID, 2, List.of("Leave policy")));
            assertTrue(events.awaitCoalesced());
            assertFalse(second.isDone());
            cache.releaseFirstMiss();

            assertEquals(
                    first.get(2, TimeUnit.SECONDS),
                    second.get(2, TimeUnit.SECONDS));
            assertEquals(1, provider.calls());
        } finally {
            cache.releaseFirstMiss();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentExactMissesShareOneProviderCall() throws Exception {
        BlockingEmbeddingPort provider = new BlockingEmbeddingPort();
        LatchingEventSink events = new LatchingEventSink();
        CachingQueryEmbeddingService service = new CachingQueryEmbeddingService(
                provider,
                new MapModelInvocationCache(),
                Duration.ofDays(7),
                Clock.fixed(NOW, ZoneOffset.UTC),
                events);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<FloatVector>> first = executor.submit(() -> service.embedAll(
                    NAMESPACE, PROFILE_ID, 2, List.of("Leave policy")));
            assertTrue(provider.awaitStarted());
            Future<List<FloatVector>> second = executor.submit(() -> service.embedAll(
                    NAMESPACE, PROFILE_ID, 2, List.of("Leave policy")));

            assertTrue(events.awaitCoalesced());
            assertFalse(second.isDone());
            provider.release();

            assertEquals(first.get(2, TimeUnit.SECONDS), second.get(2, TimeUnit.SECONDS));
            assertEquals(1, provider.calls());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void interruptedFollowerDoesNotCancelSharedProviderCall() throws Exception {
        BlockingEmbeddingPort provider = new BlockingEmbeddingPort();
        LatchingEventSink events = new LatchingEventSink();
        CachingQueryEmbeddingService service = new CachingQueryEmbeddingService(
                provider,
                new MapModelInvocationCache(),
                Duration.ofDays(7),
                Clock.fixed(NOW, ZoneOffset.UTC),
                events);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<FloatVector>> leader = executor.submit(() -> service.embedAll(
                    NAMESPACE, PROFILE_ID, 2, List.of("Leave policy")));
            assertTrue(provider.awaitStarted());
            Future<List<FloatVector>> follower = executor.submit(() -> service.embedAll(
                    NAMESPACE, PROFILE_ID, 2, List.of("Leave policy")));
            assertTrue(events.awaitCoalesced());
            follower.cancel(true);
            provider.release();

            assertEquals(1, leader.get(2, TimeUnit.SECONDS).size());
            assertTrue(follower.isCancelled());
            assertEquals(1, provider.calls());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void providerErrorCompletesFollowersExceptionally() throws Exception {
        BlockingErrorEmbeddingPort provider = new BlockingErrorEmbeddingPort();
        LatchingEventSink events = new LatchingEventSink();
        CachingQueryEmbeddingService service = new CachingQueryEmbeddingService(
                provider,
                new MapModelInvocationCache(),
                Duration.ofDays(7),
                Clock.fixed(NOW, ZoneOffset.UTC),
                events);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<FloatVector>> leader = executor.submit(() -> service.embedAll(
                    NAMESPACE, PROFILE_ID, 2, List.of("Leave policy")));
            assertTrue(provider.awaitStarted());
            Future<List<FloatVector>> follower = executor.submit(() -> service.embedAll(
                    NAMESPACE, PROFILE_ID, 2, List.of("Leave policy")));
            assertTrue(events.awaitCoalesced());
            provider.release();

            ExecutionException leaderFailure = assertThrows(
                    ExecutionException.class,
                    () -> leader.get(2, TimeUnit.SECONDS));
            ExecutionException followerFailure = assertThrows(
                    ExecutionException.class,
                    () -> follower.get(2, TimeUnit.SECONDS));
            assertTrue(leaderFailure.getCause() instanceof AssertionError);
            assertTrue(followerFailure.getCause() instanceof AssertionError);
        } finally {
            provider.release();
            executor.shutdownNow();
        }
    }

    @Test
    void boundedPersistenceFailureDoesNotLeaveCacheRowsBehind() {
        PruneFailingModelInvocationCache cache = new PruneFailingModelInvocationCache();
        RecordingEmbeddingPort provider = new RecordingEmbeddingPort("1", 2);
        CachingQueryEmbeddingService service = service(provider, cache);

        service.embedAll(NAMESPACE, PROFILE_ID, 2, List.of("Leave policy"));
        service.embedAll(NAMESPACE, PROFILE_ID, 2, List.of("Leave policy"));

        assertEquals(2, provider.calls());
        assertEquals(0, cache.size());
    }

    @Test
    void cacheReadAndWriteFailuresFallBackToProvider() {
        RecordingEventSink events = new RecordingEventSink();
        RecordingEmbeddingPort readProvider = new RecordingEmbeddingPort("1", 2);
        List<FloatVector> afterReadFailure = new CachingQueryEmbeddingService(
                        readProvider,
                        new FailingModelInvocationCache(true, false),
                        Duration.ofDays(7),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        events)
                .embedAll(NAMESPACE, PROFILE_ID, 2, List.of("Leave policy"));

        RecordingEmbeddingPort writeProvider = new RecordingEmbeddingPort("1", 2);
        List<FloatVector> afterWriteFailure = new CachingQueryEmbeddingService(
                        writeProvider,
                        new FailingModelInvocationCache(false, true),
                        Duration.ofDays(7),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        events)
                .embedAll(NAMESPACE, PROFILE_ID, 2, List.of("Leave policy"));

        assertEquals(1, afterReadFailure.size());
        assertEquals(1, afterWriteFailure.size());
        assertEquals(1, readProvider.calls());
        assertEquals(1, writeProvider.calls());
        assertTrue(events.outcomes().stream()
                .filter(outcome -> outcome == QueryEmbeddingCacheEventSink.Outcome.CACHE_FAILURE)
                .count() >= 2);
    }

    @Test
    void providerFailuresAndMalformedResponsesAreNeverCached() {
        for (FirstCallMode mode : FirstCallMode.values()) {
            MapModelInvocationCache cache = new MapModelInvocationCache();
            FirstCallPort provider = new FirstCallPort(mode, 2);
            CachingQueryEmbeddingService service = service(provider, cache);

            assertThrows(
                    RuntimeException.class,
                    () -> service.embedAll(
                            NAMESPACE,
                            PROFILE_ID,
                            2,
                            List.of("failure-" + mode.name())));

            List<FloatVector> recovered = service.embedAll(
                    NAMESPACE,
                    PROFILE_ID,
                    2,
                    List.of("failure-" + mode.name()));
            List<FloatVector> cached = service.embedAll(
                    NAMESPACE,
                    PROFILE_ID,
                    2,
                    List.of("failure-" + mode.name()));

            assertEquals(2, provider.calls(), mode.name());
            assertEquals(recovered, cached, mode.name());
        }
    }

    @Test
    void expiredEntryLoadsFromProviderAgain() {
        MapModelInvocationCache cache = new MapModelInvocationCache();
        RecordingEmbeddingPort provider = new RecordingEmbeddingPort("1", 2);
        MutableClock clock = new MutableClock(NOW);
        CachingQueryEmbeddingService service = new CachingQueryEmbeddingService(
                provider,
                cache,
                Duration.ofDays(7),
                clock);

        service.embedAll(NAMESPACE, PROFILE_ID, 2, List.of("Leave policy"));
        clock.advance(Duration.ofDays(8));
        service.embedAll(NAMESPACE, PROFILE_ID, 2, List.of("Leave policy"));

        assertEquals(2, provider.calls());
    }

    @Test
    void boundsPersistentEntriesAndNormalizesUnicodeBeforeKeyingAndLoading() {
        MapModelInvocationCache cache = new MapModelInvocationCache();
        RecordingEmbeddingPort provider = new RecordingEmbeddingPort("1", 2);
        RecordingEventSink events = new RecordingEventSink();
        CachingQueryEmbeddingService service = new CachingQueryEmbeddingService(
                provider,
                cache,
                Duration.ofDays(7),
                2,
                Clock.fixed(NOW, ZoneOffset.UTC),
                events);

        String decomposed = "Cafe\u0301";
        String composed = "Caf\u00e9";
        List<FloatVector> first = service.embedAll(
                NAMESPACE, PROFILE_ID, 2, List.of(decomposed, "second"));
        List<FloatVector> second = service.embedAll(
                NAMESPACE, PROFILE_ID, 2, List.of(composed));
        service.embedAll(NAMESPACE, PROFILE_ID, 2, List.of("third"));

        assertEquals(first.get(0), second.get(0));
        assertEquals(List.of(composed, "second"), provider.batches().get(0));
        assertEquals(List.of("third"), provider.batches().get(1));
        assertEquals(2, provider.calls());
        assertEquals(2, cache.size());
        assertEquals(2, cache.prunes());
        assertTrue(events.outcomes().contains(QueryEmbeddingCacheEventSink.Outcome.MISS));
        assertTrue(events.outcomes().contains(QueryEmbeddingCacheEventSink.Outcome.HIT));
        assertTrue(events.outcomes().contains(QueryEmbeddingCacheEventSink.Outcome.PROVIDER_CALL));
    }

    @Test
    void malformedProviderBatchIsNotPersisted() {
        MapModelInvocationCache cache = new MapModelInvocationCache();
        TextEmbeddingPort malformed = new TextEmbeddingPort() {
            @Override
            public ProcessingComponentRef component() {
                return new ProcessingComponentRef("test-embedding-model", "1");
            }

            @Override
            public List<FloatVector> embedAll(List<String> texts) {
                return List.of();
            }
        };

        assertThrows(
                IllegalStateException.class,
                () -> service(malformed, cache).embedAll(
                        NAMESPACE, PROFILE_ID, 2, List.of("not persisted")));

        assertEquals(0, cache.size());
    }

    @Test
    void keySeparatesTenantProfileComponentVersionAndDimensions() {
        MapModelInvocationCache cache = new MapModelInvocationCache();
        RecordingEmbeddingPort versionOne = new RecordingEmbeddingPort("1", 2);
        CachingQueryEmbeddingService first = service(versionOne, cache);
        first.embedAll(NAMESPACE, PROFILE_ID, 2, List.of("Leave policy"));

        first.embedAll(
                new ProjectionNamespace(
                        UUID.fromString("10000000-0000-0000-0000-000000000002"),
                        "main",
                        "knowledge"),
                PROFILE_ID,
                2,
                List.of("Leave policy"));
        first.embedAll(
                NAMESPACE,
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                2,
                List.of("Leave policy"));
        RecordingEmbeddingPort versionTwo = new RecordingEmbeddingPort("2", 2);
        service(versionTwo, cache).embedAll(
                NAMESPACE, PROFILE_ID, 2, List.of("Leave policy"));
        RecordingEmbeddingPort threeDimensions = new RecordingEmbeddingPort("1", 3);
        service(threeDimensions, cache).embedAll(
                NAMESPACE, PROFILE_ID, 3, List.of("Leave policy"));

        assertEquals(3, versionOne.calls());
        assertEquals(1, versionTwo.calls());
        assertEquals(1, threeDimensions.calls());
    }

    private static CachingQueryEmbeddingService service(
            TextEmbeddingPort provider,
            ModelInvocationCache cache) {
        return new CachingQueryEmbeddingService(
                provider,
                cache,
                Duration.ofDays(7),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class RecordingEmbeddingPort implements TextEmbeddingPort {
        private final AtomicInteger calls = new AtomicInteger();
        private final List<List<String>> batches = new ArrayList<>();
        private final String version;
        private final int dimensions;

        private RecordingEmbeddingPort(String version, int dimensions) {
            this.version = version;
            this.dimensions = dimensions;
        }

        @Override
        public ProcessingComponentRef component() {
            return new ProcessingComponentRef("test-embedding-model", version);
        }

        @Override
        public synchronized List<FloatVector> embedAll(List<String> texts) {
            calls.incrementAndGet();
            batches.add(List.copyOf(texts));
            return texts.stream()
                    .map(text -> {
                        float[] values = new float[dimensions];
                        values[0] = text.length();
                        return new FloatVector(values);
                    })
                    .toList();
        }

        int calls() {
            return calls.get();
        }

        synchronized List<List<String>> batches() {
            return List.copyOf(batches);
        }
    }

    private static final class BlockingEmbeddingPort implements TextEmbeddingPort {
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public ProcessingComponentRef component() {
            return new ProcessingComponentRef("blocking-embedding-model", "1");
        }

        @Override
        public List<FloatVector> embedAll(List<String> texts) {
            calls.incrementAndGet();
            started.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test provider was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("test provider interrupted", interrupted);
            }
            return texts.stream()
                    .map(text -> new FloatVector(new float[] {text.length(), 1.0f}))
                    .toList();
        }

        boolean awaitStarted() throws InterruptedException {
            return started.await(2, TimeUnit.SECONDS);
        }

        void release() {
            release.countDown();
        }

        int calls() {
            return calls.get();
        }
    }

    private static final class BlockingErrorEmbeddingPort implements TextEmbeddingPort {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public ProcessingComponentRef component() {
            return new ProcessingComponentRef("error-embedding-model", "1");
        }

        @Override
        public List<FloatVector> embedAll(List<String> texts) {
            started.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test provider was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("test provider interrupted", interrupted);
            }
            throw new AssertionError("provider error");
        }

        boolean awaitStarted() throws InterruptedException {
            return started.await(2, TimeUnit.SECONDS);
        }

        void release() {
            release.countDown();
        }
    }

    private static final class PruneFailingModelInvocationCache implements ModelInvocationCache {
        private final Map<Key, Entry> entries = new HashMap<>();

        @Override
        public synchronized Optional<Entry> get(Key key, Instant now) {
            Entry entry = entries.get(key);
            return entry == null || entry.expiredAt(now)
                    ? Optional.empty()
                    : Optional.of(entry);
        }

        @Override
        public synchronized void put(Key key, Entry entry) {
            entries.put(key, entry);
        }

        @Override
        public synchronized void invalidate(ProjectionNamespace namespace) {
            entries.keySet().removeIf(key -> key.namespace().equals(namespace));
        }

        @Override
        public synchronized void putBounded(
                ProjectionNamespace namespace,
                String operation,
                Map<Key, Entry> boundedEntries,
                Instant now,
                int maximumEntries) {
            Map<Key, Entry> snapshot = new HashMap<>(entries);
            entries.putAll(boundedEntries);
            try {
                prune(namespace, operation, now, maximumEntries);
            } catch (RuntimeException failure) {
                entries.clear();
                entries.putAll(snapshot);
                throw failure;
            }
        }

        @Override
        public void prune(
                ProjectionNamespace namespace,
                String operation,
                Instant now,
                int maximumEntries) {
            throw new IllegalStateException("prune unavailable");
        }

        synchronized int size() {
            return entries.size();
        }
    }

    private static final class FailingModelInvocationCache implements ModelInvocationCache {
        private final boolean failRead;
        private final boolean failWrite;

        private FailingModelInvocationCache(boolean failRead, boolean failWrite) {
            this.failRead = failRead;
            this.failWrite = failWrite;
        }

        @Override
        public Optional<Entry> get(Key key, Instant now) {
            if (failRead) {
                throw new IllegalStateException("cache read unavailable");
            }
            return Optional.empty();
        }

        @Override
        public void put(Key key, Entry entry) {
            if (failWrite) {
                throw new IllegalStateException("cache write unavailable");
            }
        }

        @Override
        public void invalidate(ProjectionNamespace namespace) {
        }
    }

    private static final class MissRegistrationRaceCache implements ModelInvocationCache {
        private final MapModelInvocationCache delegate = new MapModelInvocationCache();
        private final AtomicInteger reads = new AtomicInteger();
        private final CountDownLatch firstMissObserved = new CountDownLatch(1);
        private final CountDownLatch releaseFirstMiss = new CountDownLatch(1);

        @Override
        public Optional<Entry> get(Key key, Instant now) {
            Optional<Entry> captured = delegate.get(key, now);
            if (reads.incrementAndGet() == 1) {
                firstMissObserved.countDown();
                try {
                    releaseFirstMiss.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("interrupted test cache read");
                }
            }
            return captured;
        }

        @Override
        public void put(Key key, Entry entry) {
            delegate.put(key, entry);
        }

        @Override
        public void putBounded(
                ProjectionNamespace namespace,
                String operation,
                Map<Key, Entry> entries,
                Instant now,
                int maximumEntries) {
            throw new IllegalStateException("bounded persistence unavailable");
        }

        @Override
        public void invalidate(ProjectionNamespace namespace) {
            delegate.invalidate(namespace);
        }

        boolean awaitFirstMissObserved() throws InterruptedException {
            return firstMissObserved.await(2, TimeUnit.SECONDS);
        }

        void releaseFirstMiss() {
            releaseFirstMiss.countDown();
        }
    }

    private enum FirstCallMode {
        PROVIDER_FAILURE,
        INCOMPLETE_BATCH,
        DIMENSION_MISMATCH
    }

    private static final class FirstCallPort implements TextEmbeddingPort {
        private final FirstCallMode mode;
        private final int dimensions;
        private int calls;

        private FirstCallPort(FirstCallMode mode, int dimensions) {
            this.mode = mode;
            this.dimensions = dimensions;
        }

        @Override
        public ProcessingComponentRef component() {
            return new ProcessingComponentRef("test-provider", "1");
        }

        @Override
        public synchronized List<FloatVector> embedAll(List<String> texts) {
            calls++;
            if (calls == 1) {
                return switch (mode) {
                    case PROVIDER_FAILURE -> throw new IllegalStateException("provider failed");
                    case INCOMPLETE_BATCH -> List.of();
                    case DIMENSION_MISMATCH -> texts.stream()
                            .map(ignored -> floatVector(dimensions + 1, 99))
                            .toList();
                };
            }
            return texts.stream()
                    .map(text -> floatVector(dimensions, text.hashCode()))
                    .toList();
        }

        private static FloatVector floatVector(int dimensions, int seed) {
            float[] values = new float[dimensions];
            for (int index = 0; index < dimensions; index++) {
                values[index] = seed + index;
            }
            return new FloatVector(values);
        }

        synchronized int calls() {
            return calls;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("test clock only supports UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private static final class LatchingEventSink implements QueryEmbeddingCacheEventSink {
        private final CountDownLatch coalesced = new CountDownLatch(1);

        @Override
        public void emit(Event event) {
            if (event.outcome() == Outcome.COALESCED) {
                coalesced.countDown();
            }
        }

        boolean awaitCoalesced() throws InterruptedException {
            return coalesced.await(2, TimeUnit.SECONDS);
        }
    }

    private static final class RecordingEventSink implements QueryEmbeddingCacheEventSink {
        private final List<Event> events = new ArrayList<>();

        @Override
        public synchronized void emit(Event event) {
            events.add(event);
        }

        synchronized List<Outcome> outcomes() {
            return events.stream().map(Event::outcome).toList();
        }
    }

    private static final class MapModelInvocationCache implements ModelInvocationCache {
        private final Map<Key, Entry> entries = new HashMap<>();
        private int prunes;

        @Override
        public synchronized Optional<Entry> get(Key key, Instant now) {
            Entry entry = entries.get(key);
            return entry == null || entry.expiredAt(now)
                    ? Optional.empty()
                    : Optional.of(entry);
        }

        @Override
        public synchronized void put(Key key, Entry entry) {
            entries.put(key, entry);
        }

        @Override
        public synchronized void putBounded(
                ProjectionNamespace namespace,
                String operation,
                Map<Key, Entry> boundedEntries,
                Instant now,
                int maximumEntries) {
            entries.putAll(boundedEntries);
            prune(namespace, operation, now, maximumEntries);
        }

        @Override
        public synchronized void invalidate(ProjectionNamespace namespace) {
            entries.keySet().removeIf(key -> key.namespace().equals(namespace));
        }

        @Override
        public synchronized void prune(
                ProjectionNamespace namespace,
                String operation,
                Instant now,
                int maximumEntries) {
            prunes++;
            entries.entrySet().removeIf(entry ->
                    entry.getKey().namespace().equals(namespace)
                            && entry.getKey().operation().equals(operation)
                            && entry.getValue().expiredAt(now));
            List<Key> matching = entries.entrySet().stream()
                    .filter(entry -> entry.getKey().namespace().equals(namespace))
                    .filter(entry -> entry.getKey().operation().equals(operation))
                    .sorted((left, right) -> right.getValue()
                            .createdAt()
                            .compareTo(left.getValue().createdAt()))
                    .map(Map.Entry::getKey)
                    .toList();
            matching.stream().skip(maximumEntries).forEach(entries::remove);
        }

        synchronized int size() {
            return entries.size();
        }

        synchronized int prunes() {
            return prunes;
        }
    }
}
