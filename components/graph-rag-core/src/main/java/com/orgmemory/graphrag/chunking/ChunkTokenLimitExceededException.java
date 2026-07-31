package com.orgmemory.graphrag.chunking;

public final class ChunkTokenLimitExceededException extends RuntimeException {

    public ChunkTokenLimitExceededException(int actualTokens, int tokenLimit) {
        super("chunk token length " + actualTokens + " exceeds limit " + tokenLimit);
    }
}
