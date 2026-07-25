package com.orgmemory.graphrag.chunking;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/** Mutable startup registry. Batch processing uses only {@link ChunkerRegistrySnapshot}. */
public final class ChunkerRegistry {

    private final Map<String, TextChunker<?>> chunkers = new LinkedHashMap<>();

    public ChunkerRegistry register(TextChunker<?> chunker) {
        Objects.requireNonNull(chunker, "chunker");
        TextChunker<?> previous = chunkers.putIfAbsent(chunker.component().id(), chunker);
        if (previous != null) {
            throw new IllegalArgumentException(
                    "chunker already registered: " + chunker.component().id());
        }
        return this;
    }

    public ChunkerRegistry loadPlugins(ClassLoader classLoader) {
        ServiceLoader.load(ChunkerPlugin.class, Objects.requireNonNull(classLoader, "classLoader"))
                .stream()
                .map(ServiceLoader.Provider::get)
                .forEach(plugin -> plugin.register(this));
        return this;
    }

    public ChunkerRegistrySnapshot snapshot() {
        return new ChunkerRegistrySnapshot(chunkers);
    }
}
