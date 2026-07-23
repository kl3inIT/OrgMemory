package com.orgmemory.graphrag.chunking;

import java.util.List;
import java.util.Objects;

public record ParagraphSemanticOptions(
        int chunkTokenSize,
        int overlapTokenSize,
        int shortParagraphAnchorChars,
        boolean dropReferences,
        List<String> referenceHeadingPrefixes) implements ChunkerOptions {

    public ParagraphSemanticOptions {
        FixedTokenOptions.requireWindow(chunkTokenSize, overlapTokenSize);
        if (shortParagraphAnchorChars <= 0) {
            throw new IllegalArgumentException("short paragraph anchor length must be positive");
        }
        referenceHeadingPrefixes =
                List.copyOf(Objects.requireNonNull(referenceHeadingPrefixes, "referenceHeadingPrefixes"));
    }
}
