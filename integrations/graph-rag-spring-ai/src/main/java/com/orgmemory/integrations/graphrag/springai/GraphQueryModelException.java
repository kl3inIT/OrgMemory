package com.orgmemory.integrations.graphrag.springai;

public final class GraphQueryModelException extends RuntimeException {

    public GraphQueryModelException(String message) {
        super(message);
    }

    public GraphQueryModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
