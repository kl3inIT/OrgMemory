package com.orgmemory.api.knowledge;

import com.orgmemory.graphrag.query.QueryEmbeddingCacheEventSink;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import java.util.Objects;

/** Bounded-cardinality metrics for exact query-embedding cache operations. */
final class MicrometerQueryEmbeddingCacheEventSink
        implements QueryEmbeddingCacheEventSink {

    static final String DURATION = "orgmemory.query.embedding.cache";
    static final String ITEMS = "orgmemory.query.embedding.cache.items";

    private final MeterRegistry meters;

    MicrometerQueryEmbeddingCacheEventSink(MeterRegistry meters) {
        this.meters = Objects.requireNonNull(meters, "meters");
    }

    @Override
    public void emit(Event event) {
        Objects.requireNonNull(event, "event");
        String outcome = event.outcome().name().toLowerCase(Locale.ROOT);
        Timer.builder(DURATION)
                .description("Exact query-embedding cache operation duration")
                .tag("outcome", outcome)
                .register(meters)
                .record(event.duration());
        Counter.builder(ITEMS)
                .description("Exact query-embedding cache items by bounded outcome")
                .tag("outcome", outcome)
                .register(meters)
                .increment(event.itemCount());
    }
}
