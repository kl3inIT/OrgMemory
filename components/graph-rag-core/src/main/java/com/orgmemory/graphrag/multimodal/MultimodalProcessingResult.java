package com.orgmemory.graphrag.multimodal;

import java.util.List;
import java.util.Objects;

public record MultimodalProcessingResult(
        List<MultimodalAnalysisOutcome> outcomes,
        List<MultimodalDerivedChunk> chunks,
        boolean publishable) {

    public MultimodalProcessingResult {
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
        long successes = outcomes.stream()
                .filter(MultimodalAnalysisOutcome.Success.class::isInstance)
                .count();
        if (successes != chunks.size()) {
            throw new IllegalArgumentException(
                    "every successful analysis must materialize one derived chunk");
        }
        if (publishable
                && outcomes.stream()
                        .anyMatch(MultimodalAnalysisOutcome.Failure.class::isInstance)) {
            throw new IllegalArgumentException(
                    "a failed analysis cannot be published");
        }
    }
}
