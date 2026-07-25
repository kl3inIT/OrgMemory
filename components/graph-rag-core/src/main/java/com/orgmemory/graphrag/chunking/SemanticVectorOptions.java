package com.orgmemory.graphrag.chunking;

import java.util.Objects;
import java.util.regex.Pattern;

public record SemanticVectorOptions(
        int chunkTokenSize,
        int bufferSize,
        BreakpointThreshold threshold,
        double thresholdAmount,
        String sentenceSplitRegex) implements ChunkerOptions {

    public static final String DEFAULT_SENTENCE_SPLIT_REGEX = "(?<=[.!?。？！])\\s+";

    public SemanticVectorOptions {
        if (chunkTokenSize <= 0) {
            throw new IllegalArgumentException("chunk token size must be positive");
        }
        if (bufferSize < 0) {
            throw new IllegalArgumentException("semantic buffer size must not be negative");
        }
        Objects.requireNonNull(threshold, "threshold");
        if (!Double.isFinite(thresholdAmount) || thresholdAmount < 0) {
            throw new IllegalArgumentException("semantic threshold amount must be finite and non-negative");
        }
        sentenceSplitRegex = Objects.requireNonNull(sentenceSplitRegex, "sentenceSplitRegex");
        Pattern.compile(sentenceSplitRegex);
    }

    public enum BreakpointThreshold {
        PERCENTILE,
        STANDARD_DEVIATION,
        INTERQUARTILE,
        GRADIENT
    }
}
