package com.orgmemory.graphrag.query;

import java.time.Duration;
import java.util.Objects;

/** Payload-free telemetry for exact query-embedding cache operations. */
@FunctionalInterface
public interface QueryEmbeddingCacheEventSink {

    QueryEmbeddingCacheEventSink NO_OP = event -> { };

    void emit(Event event);

    record Event(Outcome outcome, Duration duration, int itemCount) {
        public Event {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(duration, "duration");
            if (duration.isNegative()) {
                throw new IllegalArgumentException("duration must not be negative");
            }
            if (itemCount <= 0) {
                throw new IllegalArgumentException("itemCount must be positive");
            }
        }
    }

    enum Outcome {
        HIT,
        MISS,
        COALESCED,
        PROVIDER_CALL,
        CACHE_FAILURE
    }
}
