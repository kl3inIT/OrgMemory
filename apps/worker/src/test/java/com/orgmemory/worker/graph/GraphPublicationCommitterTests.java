package com.orgmemory.worker.graph;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.GraphIndexingCoordinator;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.port.GraphProjectionPublisher;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import com.orgmemory.graphrag.port.GraphRevisionProjection;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GraphPublicationCommitterTests {

    @Test
    void invalidatesTheKnowledgeSpaceOnlyAfterPublishingAndBeforeCompleting() {
        GraphIndexingCoordinator coordinator = mock(GraphIndexingCoordinator.class);
        GraphProjectionPublisher publisher = mock(GraphProjectionPublisher.class);
        ModelInvocationCache modelCache = mock(ModelInvocationCache.class);
        RetrievalResultCache retrievalCache = mock(RetrievalResultCache.class);
        GraphRevisionProjection projection = mock(GraphRevisionProjection.class);
        GraphRevisionContributions contributions =
                mock(GraphRevisionContributions.class);
        UUID organizationId = UUID.randomUUID();
        UUID knowledgeSpaceId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Duration lease = Duration.ofMinutes(10);
        String manifest = "a".repeat(64);
        when(projection.manifestFingerprint()).thenReturn(manifest);
        when(projection.contributions()).thenReturn(contributions);
        when(contributions.organizationId()).thenReturn(organizationId);
        GraphPublicationCommitter committer = new GraphPublicationCommitter(
                coordinator, publisher, modelCache, retrievalCache);

        committer.commit(
                jobId,
                "worker-1",
                lease,
                knowledgeSpaceId,
                projection);

        ProjectionNamespace namespace = new ProjectionNamespace(
                organizationId, "default", knowledgeSpaceId.toString());
        var order = inOrder(
                coordinator, publisher, modelCache, retrievalCache);
        order.verify(coordinator)
                .preparePublication(jobId, "worker-1", lease, manifest);
        order.verify(publisher).publish(projection);
        order.verify(modelCache).invalidate(namespace);
        order.verify(retrievalCache).invalidateNamespace(namespace);
        order.verify(coordinator).complete(jobId, "worker-1");
    }
}
