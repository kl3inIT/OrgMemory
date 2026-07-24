package com.orgmemory.graphrag.neo4j;

import java.util.Objects;

public final class Neo4jGraphProjectionException extends RuntimeException {

    public Neo4jGraphProjectionException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }

    public Neo4jGraphProjectionException(String message, Throwable cause) {
        super(
                Objects.requireNonNull(message, "message"),
                Objects.requireNonNull(cause, "cause"));
    }
}
