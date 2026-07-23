package com.orgmemory.worker.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.knowledge.ClaimedGraphIndex;
import com.orgmemory.core.knowledge.EmbeddingDistanceMetric;
import com.orgmemory.core.knowledge.EmbeddingProfileRef;
import com.orgmemory.core.knowledge.GraphIndexChunk;
import com.orgmemory.core.knowledge.GraphIndexingCoordinator;
import com.orgmemory.graphrag.model.ExtractedEntity;
import com.orgmemory.graphrag.model.ExtractedRelation;
import com.orgmemory.graphrag.model.ExtractionResult;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.EntityRelationExtractor;
import com.orgmemory.graphrag.port.GraphRevisionProjection;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class GraphIndexingProcessorTests {

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID ACL_SNAPSHOT_ID = UUID.randomUUID();
    private static final UUID EMBEDDING_PROFILE_ID = UUID.randomUUID();
    private static final UUID CHUNK_ID = UUID.randomUUID();

    @Test
    void publishesOneAtomicProjectionAndCompletesTheDurableJob() {
        GraphIndexingCoordinator coordinator = mock(GraphIndexingCoordinator.class);
        GraphPublicationCommitter publications = mock(GraphPublicationCommitter.class);
        GraphExtractorFactory extractors = mock(GraphExtractorFactory.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        AiRouteResolver routes = mock(AiRouteResolver.class);
        GraphIndexingProperties properties = properties();
        ClaimedGraphIndex claim = claim();
        when(coordinator.claimNext(properties.workerId(), properties.leaseDuration()))
                .thenReturn(Optional.of(claim));
        when(routes.resolve(AiWorkload.GRAPH_EXTRACTION))
                .thenReturn(new AiRoute("openai", "gpt-5.6-sol"));
        when(routes.resolve(AiWorkload.DOCUMENT_EMBEDDING))
                .thenReturn(new AiRoute("openai", "text-embedding-3-large"));
        EntityRelationExtractor extractor = request -> new ExtractionResult(
                request.profile(),
                List.of(
                        new ExtractedEntity(
                                "source", "OrgMemory", "product",
                                "Enterprise memory platform", 0.98),
                        new ExtractedEntity(
                                "target", "Secure Search", "capability",
                                "Permission-aware retrieval", 0.97)),
                List.of(new ExtractedRelation(
                        "source",
                        "target",
                        "builds",
                        List.of("security", "retrieval"),
                        "OrgMemory builds Secure Search",
                        RelationOrientation.DIRECTED,
                        0.96)));
        when(extractors.create(new AiRoute("openai", "gpt-5.6-sol")))
                .thenReturn(extractor);
        when(embeddingModel.embed(
                        anyList(), isNull(), any(TokenCountBatchingStrategy.class)))
                .thenAnswer(invocation -> {
                    List<Document> documents = invocation.getArgument(0);
                    return documents.stream()
                            .map(ignored -> new float[] {1.0f, 0.0f, 0.0f})
                            .toList();
                });

        GraphIndexingProcessor processor = new GraphIndexingProcessor(
                coordinator,
                publications,
                extractors,
                provider(embeddingModel),
                routes,
                properties);

        processor.processNext();

        ArgumentCaptor<GraphRevisionProjection> projection =
                ArgumentCaptor.forClass(GraphRevisionProjection.class);
        verify(publications).commit(
                org.mockito.ArgumentMatchers.eq(JOB_ID),
                org.mockito.ArgumentMatchers.eq(properties.workerId()),
                org.mockito.ArgumentMatchers.eq(properties.leaseDuration()),
                projection.capture());
        assertEquals(2, projection.getValue().contributions().entities().size());
        assertEquals(1, projection.getValue().contributions().relations().size());
        assertEquals(2, projection.getValue().embeddings().entityEmbeddings().size());
        assertEquals(1, projection.getValue().embeddings().relationEmbeddings().size());
        verify(coordinator, never()).complete(any(), any());
        verify(coordinator, never()).fail(any(), any(), any(), any());
    }

    @Test
    void retriesWithoutPublishingWhenTheImmutableEmbeddingRouteDrifts() {
        GraphIndexingCoordinator coordinator = mock(GraphIndexingCoordinator.class);
        GraphPublicationCommitter publications = mock(GraphPublicationCommitter.class);
        GraphExtractorFactory extractors = mock(GraphExtractorFactory.class);
        AiRouteResolver routes = mock(AiRouteResolver.class);
        GraphIndexingProperties properties = properties();
        when(coordinator.claimNext(properties.workerId(), properties.leaseDuration()))
                .thenReturn(Optional.of(claim()));
        when(routes.resolve(AiWorkload.GRAPH_EXTRACTION))
                .thenReturn(new AiRoute("openai", "gpt-5.6-sol"));
        when(routes.resolve(AiWorkload.DOCUMENT_EMBEDDING))
                .thenReturn(new AiRoute("openai", "different-embedding-model"));
        when(extractors.create(any())).thenReturn(request ->
                new ExtractionResult(request.profile(), List.of(), List.of()));

        GraphIndexingProcessor processor = new GraphIndexingProcessor(
                coordinator,
                publications,
                extractors,
                new StaticListableBeanFactory().getBeanProvider(EmbeddingModel.class),
                routes,
                properties);

        processor.processNext();

        verify(publications, never()).commit(any(), any(), any(), any());
        verify(coordinator, never()).complete(any(), any());
        verify(coordinator).fail(
                JOB_ID,
                properties.workerId(),
                "GRAPH_INDEX_FAILED",
                "Graph extraction or publication failed; retry is scheduled");
    }

    private static ClaimedGraphIndex claim() {
        return new ClaimedGraphIndex(
                JOB_ID,
                ORGANIZATION_ID,
                ASSET_ID,
                VERSION_ID,
                REVISION_ID,
                ACL_SNAPSHOT_ID,
                1,
                1,
                new EmbeddingProfileRef(
                        EMBEDDING_PROFILE_ID,
                        ORGANIZATION_ID,
                        "openai/text-embedding-3-large/3/cosine",
                        "openai",
                        "text-embedding-3-large",
                        3,
                        EmbeddingDistanceMetric.COSINE),
                "en",
                1,
                List.of(new GraphIndexChunk(CHUNK_ID, 0, "OrgMemory builds secure retrieval.")));
    }

    private static GraphIndexingProperties properties() {
        return new GraphIndexingProperties(
                false,
                Duration.ofSeconds(3),
                "graph-worker-test",
                Duration.ofMinutes(5),
                2,
                40,
                60);
    }

    private static ObjectProvider<EmbeddingModel> provider(EmbeddingModel embeddingModel) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("embeddingModel", embeddingModel);
        return beans.getBeanProvider(EmbeddingModel.class);
    }
}
