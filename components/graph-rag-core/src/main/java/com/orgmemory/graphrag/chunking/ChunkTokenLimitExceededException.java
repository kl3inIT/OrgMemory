package com.orgmemory.graphrag.chunking;

public final class ChunkTokenLimitExceededException extends RuntimeException {

    private final int actualTokens;
    private final int tokenLimit;

    public ChunkTokenLimitExceededException(int actualTokens, int tokenLimit) {
        super("chunk token length " + actualTokens + " exceeds limit " + tokenLimit);
        this.actualTokens = actualTokens;
        this.tokenLimit = tokenLimit;
    }

    public int actualTokens() {
        return actualTokens;
    }

    public int tokenLimit() {
        return tokenLimit;
    }
}
