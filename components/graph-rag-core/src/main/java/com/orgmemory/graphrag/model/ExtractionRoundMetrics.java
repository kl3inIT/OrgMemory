package com.orgmemory.graphrag.model;

import java.time.Duration;
import java.util.Objects;

public record ExtractionRoundMetrics(
        int round,
        int estimatedInputTokens,
        int providerInputTokens,
        int providerOutputTokens,
        Duration elapsed) {

    public ExtractionRoundMetrics {
        if (round < 0) {
            throw new IllegalArgumentException("round must be non-negative");
        }
        if (estimatedInputTokens < 0
                || providerInputTokens < 0
                || providerOutputTokens < 0) {
            throw new IllegalArgumentException("token counts must be non-negative");
        }
        Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must be non-negative");
        }
    }
}
