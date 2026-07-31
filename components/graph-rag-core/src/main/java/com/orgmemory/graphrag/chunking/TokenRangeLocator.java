package com.orgmemory.graphrag.chunking;

/** Maps one encoded token window back to a covering canonical-text character span. */
@FunctionalInterface
public interface TokenRangeLocator {

    SourceSpan locate(
            String source,
            int[] tokenIds,
            int fromInclusive,
            int toExclusive);
}
