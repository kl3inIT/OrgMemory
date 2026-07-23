package com.orgmemory.graphrag.postgres;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

final class BoundedBatcher {

    private BoundedBatcher() {}

    static <T> void forEachBatch(
            List<T> values,
            int maxRecords,
            long maxPayloadBytes,
            ToLongFunction<T> payloadBytes,
            Consumer<List<T>> consumer) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(payloadBytes, "payloadBytes");
        Objects.requireNonNull(consumer, "consumer");
        if (values.isEmpty()) {
            return;
        }

        List<T> batch = new ArrayList<>(Math.min(values.size(), maxRecords));
        long batchBytes = 0;
        for (T value : values) {
            long valueBytes = Math.max(1, payloadBytes.applyAsLong(value));
            if (!batch.isEmpty()
                    && (batch.size() >= maxRecords || batchBytes + valueBytes > maxPayloadBytes)) {
                consumer.accept(List.copyOf(batch));
                batch.clear();
                batchBytes = 0;
            }
            batch.add(value);
            batchBytes = Math.min(Long.MAX_VALUE, batchBytes + valueBytes);
        }
        if (!batch.isEmpty()) {
            consumer.accept(List.copyOf(batch));
        }
    }
}
