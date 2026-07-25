package com.orgmemory.graphrag.chunking;

/** Maps one encoded token window back to an exact canonical-text character span. */
@FunctionalInterface
public interface TokenRangeLocator {

    SourceSpan locate(
            String source,
            int[] tokenIds,
            int fromInclusive,
            int toExclusive);
}
