package com.orgmemory.core.knowledge.retrieval;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

/**
 * JVM-local admission boundary for snapshot queries that may check out a JDBC
 * connection. The Spring runtime owns one instance, shared by every retrieval
 * turn in the process.
 */
final class RetrievalAdmissionControl {

    private final Semaphore permits;

    RetrievalAdmissionControl(int maximumConcurrentQueries) {
        if (maximumConcurrentQueries <= 0) {
            throw new IllegalArgumentException(
                    "maximumConcurrentQueries must be positive");
        }
        this.permits = new Semaphore(maximumConcurrentQueries, true);
    }

    <T> T execute(Callable<T> query) throws Exception {
        permits.acquire();
        try {
            return query.call();
        } finally {
            permits.release();
        }
    }

    boolean fair() {
        return permits.isFair();
    }

    int availablePermits() {
        return permits.availablePermits();
    }
}
