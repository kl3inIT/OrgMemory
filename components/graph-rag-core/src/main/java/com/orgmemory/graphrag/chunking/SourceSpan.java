package com.orgmemory.graphrag.chunking;

public record SourceSpan(int startChar, int endChar) {

    public SourceSpan {
        if (startChar < 0 || endChar <= startChar) {
            throw new IllegalArgumentException("source span must be non-empty");
        }
    }
}
