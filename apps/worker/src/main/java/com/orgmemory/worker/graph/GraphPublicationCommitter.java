package com.orgmemory.worker.graph;

import com.orgmemory.core.knowledge.GraphIndexingCoordinator;
import com.orgmemory.graphrag.port.GraphProjectionPublisher;
import com.orgmemory.graphrag.port.GraphRevisionProjection;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class GraphPublicationCommitter {

    private final GraphIndexingCoordinator coordinator;
    private final GraphProjectionPublisher publisher;

    GraphPublicationCommitter(
            GraphIndexingCoordinator coordinator,
            GraphProjectionPublisher publisher) {
        this.coordinator = coordinator;
        this.publisher = publisher;
    }

    /**
     * Commits the projection head and durable job outcome as one database
     * transaction. The PostgreSQL adapter joins this transaction.
     */
    @Transactional
    public void commit(
            UUID jobId,
            String workerId,
            Duration renewedLeaseDuration,
            GraphRevisionProjection projection) {
        coordinator.refreshLease(jobId, workerId, renewedLeaseDuration);
        publisher.publish(projection);
        coordinator.complete(jobId, workerId);
    }
}
