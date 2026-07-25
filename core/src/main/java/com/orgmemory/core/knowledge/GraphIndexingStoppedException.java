package com.orgmemory.core.knowledge;

import java.util.Objects;

/** Expected control-flow signal when a worker must stop without retrying. */
public final class GraphIndexingStoppedException extends RuntimeException {

    private final Reason reason;

    GraphIndexingStoppedException(Reason reason, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        CANCELLED,
        SUPERSEDED
    }
}
