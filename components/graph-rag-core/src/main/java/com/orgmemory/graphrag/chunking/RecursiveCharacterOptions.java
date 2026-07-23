package com.orgmemory.graphrag.chunking;

import java.util.List;
import java.util.Objects;

public record RecursiveCharacterOptions(
        int chunkTokenSize,
        int overlapTokenSize,
        List<String> separators,
        boolean keepSeparator) implements ChunkerOptions {

    public static final List<String> DEFAULT_SEPARATORS =
            List.of("\n\n", "\n", ". ", "。", "！", "？", " ", "");

    public RecursiveCharacterOptions {
        FixedTokenOptions.requireWindow(chunkTokenSize, overlapTokenSize);
        separators = List.copyOf(Objects.requireNonNull(separators, "separators"));
        if (separators.isEmpty()) {
            throw new IllegalArgumentException("at least one recursive separator is required");
        }
    }
}
