package com.orgmemory.graphrag.testkit;

import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryRetrievalResultCache implements RetrievalResultCache {

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
    public synchronized void invalidateNamespace(ProjectionNamespace namespace) {
        Objects.requireNonNull(namespace, "namespace");
        entries.keySet().removeIf(key ->
                key.snapshot().namespace().equals(namespace));
    }
}
