package com.orgmemory.integrations.graphrag.springai;

import com.orgmemory.graphrag.chunking.TextEmbeddingPort;
import com.orgmemory.graphrag.chunking.SemanticEmbeddingInvocationException;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.embedding.EmbeddingModel;

/** Bounded batch adapter from Spring AI 2.0 {@link EmbeddingModel} to the pure Java core. */
public final class SpringAiTextEmbeddingPort implements TextEmbeddingPort {

    private final EmbeddingModel model;
    private final ProcessingComponentRef component;
    private final int maximumBatchSize;

    public SpringAiTextEmbeddingPort(
            EmbeddingModel model,
            String providerId,
            String modelId,
            int maximumBatchSize) {
        this.model = Objects.requireNonNull(model, "model");
        this.component = new ProcessingComponentRef(providerId + "-" + modelId, "1");
        if (maximumBatchSize <= 0) {
            throw new IllegalArgumentException("maximumBatchSize must be positive");
        }
        this.maximumBatchSize = maximumBatchSize;
    }

    @Override
    public ProcessingComponentRef component() {
        return component;
    }

    @Override
    public List<FloatVector> embedAll(List<String> texts) {
        List<String> immutable = List.copyOf(Objects.requireNonNull(texts, "texts"));
        if (immutable.isEmpty()) {
            return List.of();
        }
        List<FloatVector> result = new ArrayList<>(immutable.size());
        Integer dimensions = null;
        try {
            for (int from = 0; from < immutable.size(); from += maximumBatchSize) {
                int to = Math.min(from + maximumBatchSize, immutable.size());
                List<float[]> batch = model.embed(immutable.subList(from, to));
                if (batch.size() != to - from) {
                    throw new IllegalStateException(
                            "embedding response count does not match request count");
                }
                for (float[] vector : batch) {
                    FloatVector immutableVector = new FloatVector(vector);
                    if (dimensions == null) {
                        dimensions = immutableVector.dimensions();
                    } else if (dimensions != immutableVector.dimensions()) {
                        throw new IllegalStateException(
                                "embedding response contains mixed vector dimensions");
                    }
                    result.add(immutableVector);
                }
            }
        } catch (RuntimeException failure) {
            throw new SemanticEmbeddingInvocationException(
                    "semantic embedding invocation failed", failure);
        }
        return List.copyOf(result);
    }
}
