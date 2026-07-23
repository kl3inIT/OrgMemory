package com.orgmemory.integrations.graphrag.springai;

public final class GraphExtractionException extends RuntimeException {

    public GraphExtractionException(String message) {
        super(message);
    }

    public GraphExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
