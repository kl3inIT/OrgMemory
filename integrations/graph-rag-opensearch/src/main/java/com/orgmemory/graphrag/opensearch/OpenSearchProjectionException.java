package com.orgmemory.graphrag.opensearch;

public final class OpenSearchProjectionException extends RuntimeException {

    OpenSearchProjectionException(String message, Throwable cause) {
        super(message, cause);
    }

    OpenSearchProjectionException(String message) {
        super(message);
    }
}
