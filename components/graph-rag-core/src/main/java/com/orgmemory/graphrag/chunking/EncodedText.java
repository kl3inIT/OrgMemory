package com.orgmemory.graphrag.chunking;

import java.util.Arrays;
import java.util.Objects;

/**
 * Token ids plus exact source-character spans.
 *
 * <p>Adapters that use byte-pair tokenizers must map decoded windows back to the canonical text
 * before constructing this value. Core chunkers never guess offsets from token counts.
 */
public final class EncodedText {

    private final String source;
    private final int[] tokenIds;
    private final int[] startChars;
    private final int[] endChars;
    private final TokenRangeLocator rangeLocator;

    public EncodedText(String source, int[] tokenIds, int[] startChars, int[] endChars) {
        this.source = Objects.requireNonNull(source, "source");
        this.tokenIds = Objects.requireNonNull(tokenIds, "tokenIds").clone();
        this.startChars = Objects.requireNonNull(startChars, "startChars").clone();
        this.endChars = Objects.requireNonNull(endChars, "endChars").clone();
        this.rangeLocator = null;
        if (this.tokenIds.length != this.startChars.length
                || this.tokenIds.length != this.endChars.length) {
            throw new IllegalArgumentException("token ids and source spans must have equal length");
        }
        int previousStart = -1;
        for (int index = 0; index < this.tokenIds.length; index++) {
            int start = this.startChars[index];
            int end = this.endChars[index];
            if (start < 0 || end <= start || end > source.length() || start < previousStart) {
                throw new IllegalArgumentException(
                        "token spans must be ordered, non-empty, and inside the source");
            }
            previousStart = start;
        }
    }

    public EncodedText(String source, int[] tokenIds, TokenRangeLocator rangeLocator) {
        this.source = Objects.requireNonNull(source, "source");
        this.tokenIds = Objects.requireNonNull(tokenIds, "tokenIds").clone();
        this.startChars = null;
        this.endChars = null;
        this.rangeLocator = Objects.requireNonNull(rangeLocator, "rangeLocator");
    }

    public int size() {
        return tokenIds.length;
    }

    public int tokenId(int index) {
        return tokenIds[index];
    }

    public int startChar(int index) {
        if (startChars == null) {
            throw new UnsupportedOperationException(
                    "this tokenizer resolves source spans by token range");
        }
        return startChars[index];
    }

    public int endChar(int index) {
        if (endChars == null) {
            throw new UnsupportedOperationException(
                    "this tokenizer resolves source spans by token range");
        }
        return endChars[index];
    }

    public String source() {
        return source;
    }

    public int[] copyTokenIds(int fromInclusive, int toExclusive) {
        checkRange(fromInclusive, toExclusive);
        return Arrays.copyOfRange(tokenIds, fromInclusive, toExclusive);
    }

    public SourceSpan sourceSpan(int fromInclusive, int toExclusive) {
        checkRange(fromInclusive, toExclusive);
        if (fromInclusive == toExclusive) {
            throw new IllegalArgumentException("an empty token range has no source span");
        }
        if (rangeLocator != null) {
            SourceSpan located =
                    rangeLocator.locate(source, tokenIds, fromInclusive, toExclusive);
            if (located.endChar() > source.length()) {
                throw new IllegalStateException("token range locator returned an invalid source span");
            }
            return located;
        }
        return new SourceSpan(startChars[fromInclusive], endChars[toExclusive - 1]);
    }

    private void checkRange(int fromInclusive, int toExclusive) {
        if (fromInclusive < 0 || toExclusive < fromInclusive || toExclusive > tokenIds.length) {
            throw new IndexOutOfBoundsException(
                    "invalid token range [" + fromInclusive + ", " + toExclusive + ")");
        }
    }
}
