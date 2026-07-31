package com.orgmemory.worker.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.graph.ClaimedGraphIndex;
import com.orgmemory.core.knowledge.retrieval.EmbeddingDistanceMetric;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef;
import com.orgmemory.core.knowledge.graph.GraphIndexChunk;
import com.orgmemory.core.knowledge.graph.GraphIndexingCoordinator;
import com.orgmemory.core.knowledge.graph.GraphProcessingProfileRef;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.model.ExtractionProfile;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import com.orgmemory.graphrag.port.GraphRevisionEmbeddings;
import com.orgmemory.graphrag.port.GraphRevisionProjection;
import com.orgmemory.graphrag.processing.LightRagGraphProcessingProfiles;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.LexicalIndex;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import com.orgmemory.graphrag.storage.VectorIndex;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GraphPublicationCommitterTests {

    @Test
    void completesAJobWithoutReextractingWhenItsSharedPublicationAlreadyExists() {
        GraphIndexingCoordinator coordinator = mock(GraphIndexingCoordinator.class);
        ProjectionPublicationStore publications =
                mock(ProjectionPublicationStore.class);
        ContentStore content = mock(ContentStore.class);
        LexicalIndex lexical = mock(LexicalIndex.class);
        VectorIndex vectors = mock(VectorIndex.class);
        GraphStore graph = mock(GraphStore.class);
        ModelInvocationCache modelCache = mock(ModelInvocationCache.class);
        RetrievalResultCache retrievalCache = mock(RetrievalResultCache.class);
        Fixture fixture = fixture();
        ProjectionNamespace namespace = new ProjectionNamespace(
                fixture.claim().organizationId(),
                "default",
                fixture.claim().knowledgeSpaceId().toString());
        ProjectionSnapshot snapshot = new ProjectionSnapshot(
                UUID.randomUUID(),
                namespace,
                8,
                "f".repeat(64),
                java.util.Set.of(
                        ProjectionKind.CONTENT,
                        ProjectionKind.LEXICAL,
                        ProjectionKind.VECTOR,
                        ProjectionKind.GRAPH),
                java.time.Instant.parse("2026-07-24T00:00:00Z"));
        when(publications.published(namespace, fixture.claim().idempotencyKey()))
                .thenReturn(Optional.of(snapshot));
        GraphPublicationCommitter committer = new GraphPublicationCommitter(
                coordinator,
                publications,
                content,
                lexical,
                vectors,
                graph,
                modelCache,
                retrievalCache);

        assertTrue(committer.completePublished(
                fixture.claim(), "worker-1", Duration.ofMinutes(10)));

        var order = inOrder(coordinator, modelCache, retrievalCache);
        order.verify(coordinator).preparePublication(
                fixture.claim().jobId(),
                "worker-1",
                Duration.ofMinutes(10),
                snapshot.manifestFingerprint());
        order.verify(modelCache).invalidate(namespace);
        order.verify(retrievalCache).invalidateNamespace(namespace);
        order.verify(coordinator).complete(fixture.claim().jobId(), "worker-1");
    }

    @Test
    void publishesAllRetrievalProjectionsBeforeInvalidatingAndCompleting() {
        GraphIndexingCoordinator coordinator = mock(GraphIndexingCoordinator.class);
        ProjectionPublicationStore publications =
                mock(ProjectionPublicationStore.class);
        ContentStore content = mock(ContentStore.class);
        LexicalIndex lexical = mock(LexicalIndex.class);
        VectorIndex vectors = mock(VectorIndex.class);
        GraphStore graph = mock(GraphStore.class);
        ModelInvocationCache modelCache = mock(ModelInvocationCache.class);
        RetrievalResultCache retrievalCache = mock(RetrievalResultCache.class);
        when(content.projectionKind()).thenReturn(ProjectionKind.CONTENT);
        when(lexical.projectionKind()).thenReturn(ProjectionKind.LEXICAL);
        when(vectors.projectionKind()).thenReturn(ProjectionKind.VECTOR);
        when(graph.projectionKind()).thenReturn(ProjectionKind.GRAPH);
        when(publications.current(any())).thenReturn(Optional.empty());
        Fixture fixture = fixture();
        GraphPublicationCommitter committer = new GraphPublicationCommitter(
                coordinator,
                publications,
                content,
                lexical,
                vectors,
                graph,
                modelCache,
                retrievalCache);

        committer.commit(
                fixture.claim(),
                "worker-1",
                Duration.ofMinutes(10),
                fixture.projection());

        ArgumentCaptor<ProjectionBatch> batch =
                ArgumentCaptor.forClass(ProjectionBatch.class);
        verify(publications).publish(batch.capture(), any());
        assertEquals(0, batch.getValue().expectedPreviousGeneration());
        assertEquals(1, batch.getValue().generation());
        assertEquals(fixture.claim().idempotencyKey(), batch.getValue().idempotencyKey());
        assertEquals(
                java.util.Set.of(
                        ProjectionKind.CONTENT,
                        ProjectionKind.LEXICAL,
                        ProjectionKind.VECTOR,
                        ProjectionKind.GRAPH),
                batch.getValue().requiredProjections());

        ProjectionNamespace namespace = new ProjectionNamespace(
                fixture.claim().organizationId(),
                "default",
                fixture.claim().knowledgeSpaceId().toString());
        var order = inOrder(
                coordinator,
                content,
                lexical,
                vectors,
                graph,
                publications,
                modelCache,
                retrievalCache);
        order.verify(coordinator).preparePublication(
                fixture.claim().jobId(),
                "worker-1",
                Duration.ofMinutes(10),
                batch.getValue().manifestFingerprint());
        order.verify(content).stageDeleteAsset(any(), org.mockito.ArgumentMatchers.eq(
                fixture.claim().knowledgeAssetId()));
        order.verify(content).stageUpsert(
                any(),
                argThat(records -> records.size() == fixture.claim().chunks().size()
                        && records.stream().allMatch(record -> Long.toString(
                                        fixture.claim().projectionGeneration())
                                .equals(record.metadata().get(
                                        ContentStore
                                                .ASSET_PROJECTION_GENERATION_METADATA_KEY)))));
        order.verify(publications).markPrepared(
                any(), org.mockito.ArgumentMatchers.eq(ProjectionKind.CONTENT), any());
        order.verify(lexical).stageDeleteAsset(any(), org.mockito.ArgumentMatchers.eq(
                fixture.claim().knowledgeAssetId()));
        order.verify(lexical).stageUpsert(any(), any());
        order.verify(publications).markPrepared(
                any(), org.mockito.ArgumentMatchers.eq(ProjectionKind.LEXICAL), any());
        order.verify(vectors).stageDeleteAsset(any(), org.mockito.ArgumentMatchers.eq(
                fixture.claim().knowledgeAssetId()));
        order.verify(vectors).stageUpsert(any(), any());
        order.verify(publications).markPrepared(
                any(), org.mockito.ArgumentMatchers.eq(ProjectionKind.VECTOR), any());
        order.verify(graph).stageDeleteAsset(any(), org.mockito.ArgumentMatchers.eq(
                fixture.claim().knowledgeAssetId()));
        order.verify(graph).stageReplaceRevision(
                any(), org.mockito.ArgumentMatchers.eq(fixture.projection().contributions()));
        order.verify(publications).markPrepared(
                any(), org.mockito.ArgumentMatchers.eq(ProjectionKind.GRAPH), any());
        order.verify(publications).publish(any(), any());
        order.verify(modelCache).invalidate(namespace);
        order.verify(retrievalCache).invalidateNamespace(namespace);
        order.verify(coordinator).complete(fixture.claim().jobId(), "worker-1");
    }

    private static Fixture fixture() {
        UUID organizationId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        long projectionGeneration = 4;
        EmbeddingProfileRef profile = new EmbeddingProfileRef(
                profileId,
                organizationId,
                "openai/text-embedding-3-large/3/cosine",
                "openai",
                "text-embedding-3-large",
                3,
                EmbeddingDistanceMetric.COSINE);
        var processingProfile = LightRagGraphProcessingProfiles.current(
                new ExtractionProfile("openai", "gpt-test", "orgmemory-lightrag-v1.5.4-json-v1", 4, 6));
        var processingProfileRef = new GraphProcessingProfileRef(
                UUID.randomUUID(),
                processingProfile.canonicalSha256(),
                processingProfile);
        String idempotencyKey = "graph:"
                + organizationId
                + ":"
                + revisionId
                + ":"
                + projectionGeneration
                + ":"
                + processingProfile.canonicalSha256();
        ClaimedGraphIndex claim = new ClaimedGraphIndex(
                UUID.randomUUID(),
                organizationId,
                assetId,
                spaceId,
                UUID.randomUUID(),
                revisionId,
                UUID.randomUUID(),
                2,
                projectionGeneration,
                processingProfileRef,
                idempotencyKey,
                profile,
                "en",
                1,
                List.of(new GraphIndexChunk(
                        UUID.randomUUID(),
                        0,
                        "Secure company knowledge",
                        "Overview",
                        3,
                        new FloatVector(new float[] {1.0f, 0.0f, 0.0f}))));
        GraphRevisionProjection projection = new GraphRevisionProjection(
                new GraphRevisionContributions(
                        organizationId,
                        assetId,
                        revisionId,
                        projectionGeneration,
                        List.of(),
                        List.of()),
                new GraphRevisionEmbeddings(
                        organizationId,
                        assetId,
                        revisionId,
                        projectionGeneration,
                        profileId,
                        3,
                        List.of(),
                        List.of()),
                processingProfile.canonicalSha256());
        return new Fixture(claim, projection);
    }

    private record Fixture(
            ClaimedGraphIndex claim,
            GraphRevisionProjection projection) {}
}
