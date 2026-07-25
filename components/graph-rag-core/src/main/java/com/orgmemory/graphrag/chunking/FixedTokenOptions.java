package com.orgmemory.graphrag.chunking;

public record FixedTokenOptions(
        int chunkTokenSize,
        int overlapTokenSize,
        String splitBy,
        boolean splitOnly) implements ChunkerOptions {

    public FixedTokenOptions {
        requireWindow(chunkTokenSize, overlapTokenSize);
        splitBy = splitBy == null || splitBy.isEmpty() ? null : splitBy;
        if (splitOnly && splitBy == null) {
            throw new IllegalArgumentException("splitOnly requires a split delimiter");
        }
    }

    static void requireWindow(int size, int overlap) {
        if (size <= 0) {
            throw new IllegalArgumentException("chunk token size must be positive");
        }
        if (overlap < 0 || overlap >= size) {
            throw new IllegalArgumentException(
                    "chunk overlap must be non-negative and smaller than chunk size");
        }
    }
}
