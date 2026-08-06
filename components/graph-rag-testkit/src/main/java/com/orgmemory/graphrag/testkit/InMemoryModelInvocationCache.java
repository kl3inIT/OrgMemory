package com.orgmemory.graphrag.testkit;

import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryModelInvocationCache implements ModelInvocationCache {

    private final Map<Key, Entry> entries = new HashMap<>();

    @Override
    public synchronized Optional<Entry> get(Key key, Instant now) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(now, "now");
        Entry entry = entries.get(key);
        if (entry == null || entry.expiredAt(now)) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    @Override
    public synchronized void put(Key key, Entry entry) {
        entries.put(
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(entry, "entry"));
    }

    @Override
    public synchronized void putBounded(
            ProjectionNamespace namespace,
            String operation,
            Map<Key, Entry> boundedEntries,
            Instant now,
            int maximumEntries) {
        Objects.requireNonNull(namespace, "namespace");
        String boundedOperation = Objects.requireNonNull(operation, "operation").strip();
        Map<Key, Entry> validatedEntries =
                Map.copyOf(Objects.requireNonNull(boundedEntries, "boundedEntries"));
        Objects.requireNonNull(now, "now");
        if (boundedOperation.isEmpty()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        validatedEntries.forEach((key, entry) -> {
            if (!key.namespace().equals(namespace)
                    || !key.operation().equals(boundedOperation)) {
                throw new IllegalArgumentException(
                        "bounded entries must match namespace and operation");
            }
            Objects.requireNonNull(entry, "entry");
        });
        entries.putAll(validatedEntries);
        entries.entrySet().removeIf(entry ->
                entry.getKey().namespace().equals(namespace)
                        && entry.getKey().operation().equals(boundedOperation)
                        && entry.getValue().expiredAt(now));
        entries.entrySet().stream()
                .filter(entry -> entry.getKey().namespace().equals(namespace))
                .filter(entry -> entry.getKey().operation().equals(boundedOperation))
                .sorted((left, right) -> right.getValue()
                        .createdAt()
                        .compareTo(left.getValue().createdAt()))
                .skip(maximumEntries)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(entries::remove);
    }

    @Override
    public synchronized void invalidate(ProjectionNamespace namespace) {
        Objects.requireNonNull(namespace, "namespace");
        entries.keySet().removeIf(key -> key.namespace().equals(namespace));
    }
}
