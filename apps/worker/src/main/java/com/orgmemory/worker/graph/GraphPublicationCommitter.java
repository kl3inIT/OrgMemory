package com.orgmemory.worker.graph;

import com.orgmemory.core.knowledge.GraphIndexingCoordinator;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.port.GraphProjectionPublisher;
import com.orgmemory.graphrag.port.GraphRevisionProjection;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class GraphPublicationCommitter {

    private final GraphIndexingCoordinator coordinator;
    private final GraphProjectionPublisher publisher;
    private final ModelInvocationCache modelCache;
    private final RetrievalResultCache retrievalCache;

    GraphPublicationCommitter(
            GraphIndexingCoordinator coordinator,
            GraphProjectionPublisher publisher,
            ModelInvocationCache modelCache,
            RetrievalResultCache retrievalCache) {
        this.coordinator = coordinator;
        this.publisher = publisher;
        this.modelCache = modelCache;
        this.retrievalCache = retrievalCache;
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
            UUID knowledgeSpaceId,
            GraphRevisionProjection projection) {
        coordinator.preparePublication(
                jobId,
                workerId,
                renewedLeaseDuration,
                projection.manifestFingerprint());
        publisher.publish(projection);
        ProjectionNamespace namespace = new ProjectionNamespace(
                projection.contributions().organizationId(),
                "default",
                knowledgeSpaceId.toString());
        modelCache.invalidate(namespace);
        retrievalCache.invalidateNamespace(namespace);
        coordinator.complete(jobId, workerId);
    }
}
