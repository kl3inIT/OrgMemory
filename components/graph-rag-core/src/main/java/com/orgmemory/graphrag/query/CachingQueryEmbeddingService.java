package com.orgmemory.graphrag.query;

import static com.orgmemory.graphrag.cache.ModelInvocationCacheKeys.QUERY_EMBEDDING_OPERATION;

import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.ModelInvocationCacheKeys;
import com.orgmemory.graphrag.chunking.TextEmbeddingPort;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/** Exact, permission-namespace-scoped cache for query embeddings. */
public final class CachingQueryEmbeddingService {

    private static final String MEDIA_TYPE = "application/vnd.orgmemory.float32-vector";
    private static final int DEFAULT_MAXIMUM_ENTRIES = 10_000;

    private final TextEmbeddingPort provider;
    private final ModelInvocationCache cache;
    private final Duration timeToLive;
    private final int maximumEntries;
    private final Clock clock;
    private final QueryEmbeddingCacheEventSink events;
    private final ConcurrentHashMap<ModelInvocationCache.Key, CompletableFuture<FloatVector>>
            inFlight = new ConcurrentHashMap<>();

    public CachingQueryEmbeddingService(
            TextEmbeddingPort provider,
            ModelInvocationCache cache,
            Duration timeToLive,
            Clock clock) {
        this(
                provider,
                cache,
                timeToLive,
                DEFAULT_MAXIMUM_ENTRIES,
                clock,
                QueryEmbeddingCacheEventSink.NO_OP);
    }

    public CachingQueryEmbeddingService(
            TextEmbeddingPort provider,
            ModelInvocationCache cache,
            Duration timeToLive,
            Clock clock,
            QueryEmbeddingCacheEventSink events) {
        this(provider, cache, timeToLive, DEFAULT_MAXIMUM_ENTRIES, clock, events);
    }

    public CachingQueryEmbeddingService(
            TextEmbeddingPort provider,
            ModelInvocationCache cache,
            Duration timeToLive,
            int maximumEntries,
            Clock clock,
            QueryEmbeddingCacheEventSink events) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.timeToLive = Objects.requireNonNull(timeToLive, "timeToLive");
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            throw new IllegalArgumentException("timeToLive must be positive");
        }
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        this.maximumEntries = maximumEntries;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.events = Objects.requireNonNull(events, "events");
    }

    public List<FloatVector> embedAll(
            ProjectionNamespace namespace,
            UUID embeddingProfileId,
            int dimensions,
            List<String> texts) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(embeddingProfileId, "embeddingProfileId");
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        List<String> requested = List.copyOf(Objects.requireNonNull(texts, "texts"));
        if (requested.isEmpty()) {
            return List.of();
        }
        List<String> inputs = requested.stream()
                .map(CachingQueryEmbeddingService::normalizeInput)
                .toList();
        Instant now = clock.instant();
        Map<String, ModelInvocationCache.Key> keys = new LinkedHashMap<>();
        for (String input : inputs) {
            keys.computeIfAbsent(
                    input,
                    ignored -> ModelInvocationCacheKeys.queryEmbedding(
                            namespace,
                            input,
                            provider.component().toString(),
                            embeddingProfileId,
                            dimensions));
        }
        Map<String, FloatVector> vectors = new LinkedHashMap<>();
        Map<String, CompletableFuture<FloatVector>> waiting = new LinkedHashMap<>();
        List<String> ownedMisses = new ArrayList<>();
        for (Map.Entry<String, ModelInvocationCache.Key> keyedInput : keys.entrySet()) {
            String input = keyedInput.getKey();
            ModelInvocationCache.Key key = keyedInput.getValue();
            CompletableFuture<FloatVector> owned = new CompletableFuture<>();
            CompletableFuture<FloatVector> existing = inFlight.putIfAbsent(key, owned);
            if (existing != null) {
                waiting.put(input, existing);
                emit(QueryEmbeddingCacheEventSink.Outcome.COALESCED, System.nanoTime(), 1);
                continue;
            }
            try {
                long lookupStarted = System.nanoTime();
                Optional<FloatVector> cached = readCached(key, now, dimensions);
                emit(
                        cached.isPresent()
                                ? QueryEmbeddingCacheEventSink.Outcome.HIT
                                : QueryEmbeddingCacheEventSink.Outcome.MISS,
                        lookupStarted,
                        1);
                if (cached.isPresent()) {
                    FloatVector vector = cached.orElseThrow();
                    vectors.put(input, vector);
                    owned.complete(vector);
                    inFlight.remove(key, owned);
                } else {
                    waiting.put(input, owned);
                    ownedMisses.add(input);
                }
            } catch (RuntimeException | Error failure) {
                owned.completeExceptionally(failure);
                inFlight.remove(key, owned);
                throw failure;
            }
        }
        if (!ownedMisses.isEmpty()) {
            loadOwnedMisses(
                    namespace,
                    ownedMisses,
                    keys,
                    waiting,
                    dimensions,
                    now);
        }
        waiting.forEach((input, future) -> vectors.put(input, await(future)));
        List<FloatVector> result = new ArrayList<>(inputs.size());
        inputs.forEach(input -> result.add(vectors.get(input)));
        return List.copyOf(result);
    }

    private void loadOwnedMisses(
            ProjectionNamespace namespace,
            List<String> ownedMisses,
            Map<String, ModelInvocationCache.Key> keys,
            Map<String, CompletableFuture<FloatVector>> waiting,
            int dimensions,
            Instant now) {
        try {
            List<FloatVector> loaded;
            long providerStarted = System.nanoTime();
            try {
                loaded = provider.embedAll(ownedMisses);
            } finally {
                emit(
                        QueryEmbeddingCacheEventSink.Outcome.PROVIDER_CALL,
                        providerStarted,
                        ownedMisses.size());
            }
            if (loaded.size() != ownedMisses.size()) {
                throw new IllegalStateException("embedding adapter returned an incomplete batch");
            }
            loaded.forEach(vector -> requireDimensions(vector, dimensions));
            writeBounded(namespace, ownedMisses, loaded, keys, now);
            for (int index = 0; index < ownedMisses.size(); index++) {
                waiting.get(ownedMisses.get(index)).complete(loaded.get(index));
            }
        } catch (RuntimeException | Error failure) {
            ownedMisses.forEach(input -> waiting.get(input).completeExceptionally(failure));
            throw failure;
        } finally {
            ownedMisses.forEach(input -> inFlight.remove(keys.get(input), waiting.get(input)));
        }
    }

    private Optional<FloatVector> readCached(
            ModelInvocationCache.Key key,
            Instant now,
            int dimensions) {
        long started = System.nanoTime();
        try {
            return cache.get(key, now)
                    .filter(entry -> MEDIA_TYPE.equals(entry.mediaType()))
                    .map(ModelInvocationCache.Entry::payload)
                    .map(payload -> decode(payload, dimensions));
        } catch (RuntimeException cacheFailure) {
            emit(QueryEmbeddingCacheEventSink.Outcome.CACHE_FAILURE, started, 1);
            return Optional.empty();
        }
    }

    private void writeBounded(
            ProjectionNamespace namespace,
            List<String> inputs,
            List<FloatVector> vectors,
            Map<String, ModelInvocationCache.Key> keys,
            Instant now) {
        long started = System.nanoTime();
        try {
            Map<ModelInvocationCache.Key, ModelInvocationCache.Entry> entries =
                    new LinkedHashMap<>();
            for (int index = 0; index < inputs.size(); index++) {
                entries.put(
                        keys.get(inputs.get(index)),
                        new ModelInvocationCache.Entry(
                                MEDIA_TYPE,
                                encode(vectors.get(index)),
                                now,
                                now.plus(timeToLive)));
            }
            cache.putBounded(
                    namespace,
                    QUERY_EMBEDDING_OPERATION,
                    entries,
                    now,
                    maximumEntries);
        } catch (RuntimeException cacheFailure) {
            emit(QueryEmbeddingCacheEventSink.Outcome.CACHE_FAILURE, started, inputs.size());
            // Atomic bounded persistence is optional; the provider result remains valid.
        }
    }

    private void emit(
            QueryEmbeddingCacheEventSink.Outcome outcome,
            long startedNanos,
            int itemCount) {
        try {
            long elapsed = Math.max(0L, System.nanoTime() - startedNanos);
            events.emit(new QueryEmbeddingCacheEventSink.Event(
                    outcome,
                    Duration.ofNanos(elapsed),
                    itemCount));
        } catch (RuntimeException telemetryFailure) {
            // Telemetry must never become a query dependency.
        }
    }

    private static String normalizeInput(String input) {
        String normalized = Normalizer.normalize(
                Objects.requireNonNull(input, "input"),
                Normalizer.Form.NFC);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        return normalized;
    }

    private static FloatVector await(CompletableFuture<FloatVector> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CancellationException("interrupted while waiting for shared embedding");
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("shared embedding failed", cause);
        }
    }

    private static void requireDimensions(FloatVector vector, int dimensions) {
        if (vector.dimensions() != dimensions) {
            throw new IllegalStateException(
                    "query embedding dimensions do not match the pinned profile");
        }
    }

    private static String encode(FloatVector vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.dimensions() * Float.BYTES)
                .order(ByteOrder.BIG_ENDIAN);
        for (float value : vector.copyValues()) {
            buffer.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    private static FloatVector decode(String payload, int dimensions) {
        long expectedBytes = (long) dimensions * Float.BYTES;
        long expectedEncodedCharacters = ((expectedBytes + 2L) / 3L) * 4L;
        if (payload.length() != expectedEncodedCharacters) {
            throw new IllegalStateException("cached embedding payload length is invalid");
        }
        byte[] bytes = Base64.getDecoder().decode(payload);
        if (bytes.length != expectedBytes) {
            throw new IllegalStateException("cached embedding dimensions are invalid");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        float[] values = new float[dimensions];
        for (int index = 0; index < dimensions; index++) {
            values[index] = buffer.getFloat();
        }
        return new FloatVector(values);
    }
}
