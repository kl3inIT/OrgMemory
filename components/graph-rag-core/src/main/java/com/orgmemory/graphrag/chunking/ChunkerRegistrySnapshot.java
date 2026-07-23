package com.orgmemory.graphrag.chunking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable chunker table pinned for the lifetime of one processing batch. */
public final class ChunkerRegistrySnapshot {

    private final Map<String, TextChunker<?>> chunkers;

    ChunkerRegistrySnapshot(Map<String, TextChunker<?>> chunkers) {
        this.chunkers = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(chunkers));
    }

    public TextChunker<?> require(String chunkerId) {
        String key = Objects.requireNonNull(chunkerId, "chunkerId").trim().toLowerCase();
        TextChunker<?> chunker = chunkers.get(key);
        if (chunker == null) {
            throw new IllegalArgumentException("unknown chunker " + key);
        }
        return chunker;
    }

    public <O extends ChunkerOptions> List<ChunkedText> execute(
            String chunkerId,
            ChunkingRequest request,
            O options) {
        TextChunker<?> untyped = require(chunkerId);
        if (!untyped.optionsType().isInstance(options)) {
            throw new IllegalArgumentException(
                    "chunker "
                            + chunkerId
                            + " requires "
                            + untyped.optionsType().getSimpleName());
        }
        return executeTyped(untyped, request, options);
    }

    @SuppressWarnings("unchecked")
    private static <O extends ChunkerOptions> List<ChunkedText> executeTyped(
            TextChunker<?> chunker,
            ChunkingRequest request,
            O options) {
        return ((TextChunker<O>) chunker).chunk(request, options);
    }

    public List<TextChunker<?>> chunkers() {
        return List.copyOf(chunkers.values());
    }
}
