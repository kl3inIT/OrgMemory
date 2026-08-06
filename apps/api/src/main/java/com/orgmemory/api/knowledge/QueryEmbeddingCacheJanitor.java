package com.orgmemory.api.knowledge;

import static com.orgmemory.graphrag.cache.ModelInvocationCacheKeys.QUERY_EMBEDDING_OPERATION;

import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.query.QueryEmbeddingCacheEventSink;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;

final class QueryEmbeddingCacheJanitor {

    private final ModelInvocationCache cache;
    private final QueryEmbeddingCacheProperties properties;
    private final QueryEmbeddingCacheEventSink events;
    private final Clock clock;

    QueryEmbeddingCacheJanitor(
            ModelInvocationCache cache,
            QueryEmbeddingCacheProperties properties,
            QueryEmbeddingCacheEventSink events,
            Clock clock) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(
            fixedDelayString =
                    "${orgmemory.query-embedding-cache.cleanup-interval:15m}",
            initialDelayString =
                    "${orgmemory.query-embedding-cache.cleanup-interval:15m}")
    void deleteExpired() {
        long started = System.nanoTime();
        try {
            cache.deleteExpired(
                    QUERY_EMBEDDING_OPERATION,
                    clock.instant(),
                    properties.cleanupBatchSize());
        } catch (RuntimeException cacheFailure) {
            emitFailure(started);
        }
    }

    private void emitFailure(long startedNanos) {
        try {
            events.emit(new QueryEmbeddingCacheEventSink.Event(
                    QueryEmbeddingCacheEventSink.Outcome.CACHE_FAILURE,
                    Duration.ofNanos(Math.max(0L, System.nanoTime() - startedNanos)),
                    1));
        } catch (RuntimeException telemetryFailure) {
            // Cache cleanup and telemetry are both best-effort query-independent work.
        }
    }
}
