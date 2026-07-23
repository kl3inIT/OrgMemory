package com.orgmemory.graphrag.testkit;

import com.orgmemory.graphrag.chunking.TextEmbeddingPort;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Caller-supplied deterministic vectors for semantic chunker conformance tests. */
public final class DeterministicEmbeddingPort implements TextEmbeddingPort {

    public static final ProcessingComponentRef COMPONENT =
            new ProcessingComponentRef("test-embedding", "1");
    private final Function<String, float[]> embedding;

    public DeterministicEmbeddingPort(Function<String, float[]> embedding) {
        this.embedding = Objects.requireNonNull(embedding, "embedding");
    }

    @Override
    public ProcessingComponentRef component() {
        return COMPONENT;
    }

    @Override
    public List<FloatVector> embedAll(List<String> texts) {
        return texts.stream()
                .map(text -> new FloatVector(embedding.apply(text)))
                .toList();
    }
}
