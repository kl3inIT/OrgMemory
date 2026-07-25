package com.orgmemory.worker.graph;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
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
import com.orgmemory.core.knowledge.GraphProcessingProfileRef;
import com.orgmemory.graphrag.extraction.LightRagExtractionPrompt;
import com.orgmemory.graphrag.model.ExtractedEntity;
import com.orgmemory.graphrag.model.ExtractedRelation;
import com.orgmemory.graphrag.model.ExtractionResult;
import com.orgmemory.graphrag.model.ExtractionProfile;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.observability.GraphRagEventSink;
import com.orgmemory.graphrag.port.EntityRelationExtractor;
import com.orgmemory.graphrag.port.GraphRevisionProjection;
import com.orgmemory.graphrag.processing.LightRagGraphProcessingProfiles;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID ACL_SNAPSHOT_ID = UUID.randomUUID();
    private static final UUID EMBEDDING_PROFILE_ID = UUID.randomUUID();
    private static final UUID CHUNK_ID = UUID.randomUUID();
    private static final UUID SECOND_CHUNK_ID = UUID.randomUUID();

    @Test
    void publishesOneAtomicProjectionAndCompletesTheDurableJob() {
        GraphIndexingCoordinator coordinator = mock(GraphIndexingCoordinator.class);
        GraphPublicationCommitter publications = mock(GraphPublicationCommitter.class);
        GraphExtractorFactory extractors = mock(GraphExtractorFactory.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        AiRouteResolver routes = mock(AiRouteResolver.class);
        GraphIndexingProperties properties = properties();
        GraphRagEventSink events = mock(GraphRagEventSink.class);
        ClaimedGraphIndex claim = claim(List.of(
                chunk(
                        CHUNK_ID,
                        0,
                        "OrgMemory builds secure retrieval.",
                        "Engineering > Search"),
                chunk(
                        SECOND_CHUNK_ID,
                        1,
                        "OrgMemory also builds secure retrieval.",
                        "Engineering > Search")));
        when(coordinator.claimNext(properties.workerId(), properties.leaseDuration()))
                .thenReturn(Optional.of(claim));
        when(routes.resolve(AiWorkload.GRAPH_EXTRACTION))
                .thenReturn(new AiRoute("openai", "gpt-5.6-sol"));
        when(routes.resolve(AiWorkload.DOCUMENT_EMBEDDING))
                .thenReturn(new AiRoute("openai", "text-embedding-3-large"));
        EntityRelationExtractor extractor = request -> {
            assertEquals("Engineering > Search", request.sectionContext());
            return new ExtractionResult(
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
        };
        when(extractors.create(new AiRoute("openai", "gpt-test")))
                .thenReturn(extractor);
        when(embeddingModel.embed(
                        anyList(), isNull(), any(TokenCountBatchingStrategy.class)))
                .thenAnswer(invocation -> {
                    List<Document> documents = invocation.getArgument(0);
                    assertEquals(6, documents.size());
                    assertTrue(documents.stream()
                            .map(Document::getText)
                            .anyMatch("orgmemory\nEnterprise memory platform"::equals));
                    assertTrue(documents.stream()
                            .map(Document::getText)
                            .anyMatch("secure search\nPermission-aware retrieval"::equals));
                    assertEquals(
                            "retrieval, security\torgmemory\nsecure search\n"
                                    + "OrgMemory builds Secure Search",
                            documents.getLast().getText());
                    assertFalse(documents.stream()
                            .map(Document::getText)
                            .anyMatch(text -> text.contains("\nproduct\n")
                                    || text.contains("\ncapability\n")
                                    || text.contains("\tbuilds\n")));
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
                properties,
                events);

        processor.processNext();

        ArgumentCaptor<GraphRevisionProjection> projection =
                ArgumentCaptor.forClass(GraphRevisionProjection.class);
        verify(publications).commit(
                org.mockito.ArgumentMatchers.eq(claim),
                org.mockito.ArgumentMatchers.eq(properties.workerId()),
                org.mockito.ArgumentMatchers.eq(properties.leaseDuration()),
                projection.capture());
        assertEquals(4, projection.getValue().contributions().entities().size());
        assertEquals(2, projection.getValue().contributions().relations().size());
        assertEquals(4, projection.getValue().embeddings().entityEmbeddings().size());
        assertEquals(2, projection.getValue().embeddings().relationEmbeddings().size());
        verify(coordinator, never()).complete(any(), any());
        verify(coordinator, never()).fail(any(), any(), any(), any());
        ArgumentCaptor<GraphRagEventSink.GraphRagEvent> emitted =
                ArgumentCaptor.forClass(GraphRagEventSink.GraphRagEvent.class);
        verify(events, times(4)).emit(emitted.capture());
        assertEquals(
                List.of(
                        GraphRagEventSink.Stage.EXTRACT,
                        GraphRagEventSink.Stage.MERGE,
                        GraphRagEventSink.Stage.EMBED,
                        GraphRagEventSink.Stage.PUBLISH),
                emitted.getAllValues().stream()
                        .map(GraphRagEventSink.GraphRagEvent::stage)
                        .toList());
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
                properties,
                GraphRagEventSink.NO_OP);

        processor.processNext();

        verify(publications, never()).commit(any(), any(), any(), any());
        verify(coordinator, never()).complete(any(), any());
        verify(coordinator).fail(
                JOB_ID,
                properties.workerId(),
                "GRAPH_INDEX_FAILED",
                "Graph extraction or publication failed; retry is scheduled");
    }

    @Test
    void refreshesTheLeaseWhileAChunkExtractionIsStillRunning() throws Exception {
        GraphIndexingCoordinator coordinator = mock(GraphIndexingCoordinator.class);
        GraphPublicationCommitter publications = mock(GraphPublicationCommitter.class);
        GraphExtractorFactory extractors = mock(GraphExtractorFactory.class);
        AiRouteResolver routes = mock(AiRouteResolver.class);
        GraphIndexingProperties properties = properties(Duration.ofMillis(90));
        CountDownLatch extractionStarted = new CountDownLatch(1);
        CountDownLatch releaseExtraction = new CountDownLatch(1);
        when(coordinator.claimNext(properties.workerId(), properties.leaseDuration()))
                .thenReturn(Optional.of(claim()));
        when(routes.resolve(AiWorkload.GRAPH_EXTRACTION))
                .thenReturn(new AiRoute("openai", "gpt-5.6-sol"));
        when(routes.resolve(AiWorkload.DOCUMENT_EMBEDDING))
                .thenReturn(new AiRoute("openai", "text-embedding-3-large"));
        when(extractors.create(any())).thenReturn(request -> {
            extractionStarted.countDown();
            try {
                if (!releaseExtraction.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test extraction was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("test extraction interrupted", interrupted);
            }
            return new ExtractionResult(request.profile(), List.of(), List.of());
        });
        GraphIndexingProcessor processor = new GraphIndexingProcessor(
                coordinator,
                publications,
                extractors,
                new StaticListableBeanFactory().getBeanProvider(EmbeddingModel.class),
                routes,
                properties,
                GraphRagEventSink.NO_OP);

        Thread worker = Thread.ofVirtual().start(processor::processNext);
        try {
            assertTrue(extractionStarted.await(1, TimeUnit.SECONDS));
            verify(coordinator, timeout(1_000).atLeastOnce())
                    .refreshLease(
                            JOB_ID,
                            properties.workerId(),
                            properties.leaseDuration());
        } finally {
            releaseExtraction.countDown();
            worker.join(5_000);
        }

        assertFalse(worker.isAlive());
        verify(publications).commit(any(), any(), any(), any());
    }

    @Test
    void cancelsAnExtractionThatExceedsItsConfiguredDeadline() {
        GraphIndexingCoordinator coordinator = mock(GraphIndexingCoordinator.class);
        GraphPublicationCommitter publications = mock(GraphPublicationCommitter.class);
        GraphExtractorFactory extractors = mock(GraphExtractorFactory.class);
        AiRouteResolver routes = mock(AiRouteResolver.class);
        GraphIndexingProperties properties =
                properties(Duration.ofMillis(90), Duration.ofMillis(45));
        AtomicBoolean interrupted = new AtomicBoolean();
        when(coordinator.claimNext(properties.workerId(), properties.leaseDuration()))
                .thenReturn(Optional.of(claim()));
        when(routes.resolve(AiWorkload.GRAPH_EXTRACTION))
                .thenReturn(new AiRoute("openai", "gpt-5.6-sol"));
        when(extractors.create(any())).thenReturn(request -> {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException cancellation) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "test extraction cancelled", cancellation);
            }
            return new ExtractionResult(request.profile(), List.of(), List.of());
        });
        GraphIndexingProcessor processor = new GraphIndexingProcessor(
                coordinator,
                publications,
                extractors,
                new StaticListableBeanFactory().getBeanProvider(EmbeddingModel.class),
                routes,
                properties,
                GraphRagEventSink.NO_OP);

        processor.processNext();

        assertTrue(interrupted.get());
        verify(publications, never()).commit(any(), any(), any(), any());
        verify(coordinator).fail(
                JOB_ID,
                properties.workerId(),
                "GRAPH_INDEX_FAILED",
                "Graph extraction or publication failed; retry is scheduled");
    }

    @Test
    void interruptionLeavesTheClaimedJobForLeaseBasedRetry() throws Exception {
        GraphIndexingCoordinator coordinator = mock(GraphIndexingCoordinator.class);
        GraphPublicationCommitter publications = mock(GraphPublicationCommitter.class);
        GraphExtractorFactory extractors = mock(GraphExtractorFactory.class);
        AiRouteResolver routes = mock(AiRouteResolver.class);
        GraphIndexingProperties properties = properties(Duration.ofMinutes(5));
        CountDownLatch extractionStarted = new CountDownLatch(1);
        CountDownLatch releaseExtraction = new CountDownLatch(1);
        AtomicBoolean interruptRestored = new AtomicBoolean();
        when(coordinator.claimNext(properties.workerId(), properties.leaseDuration()))
                .thenReturn(Optional.of(claim()));
        when(routes.resolve(AiWorkload.GRAPH_EXTRACTION))
                .thenReturn(new AiRoute("openai", "gpt-5.6-sol"));
        when(extractors.create(any())).thenReturn(request -> {
            extractionStarted.countDown();
            try {
                releaseExtraction.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return new ExtractionResult(request.profile(), List.of(), List.of());
        });
        GraphIndexingProcessor processor = new GraphIndexingProcessor(
                coordinator,
                publications,
                extractors,
                new StaticListableBeanFactory().getBeanProvider(EmbeddingModel.class),
                routes,
                properties,
                GraphRagEventSink.NO_OP);

        Thread worker = Thread.ofVirtual().start(() -> {
            processor.processNext();
            interruptRestored.set(Thread.currentThread().isInterrupted());
        });
        try {
            assertTrue(extractionStarted.await(1, TimeUnit.SECONDS));
            worker.interrupt();
            worker.join(5_000);
        } finally {
            releaseExtraction.countDown();
        }

        assertFalse(worker.isAlive());
        assertTrue(interruptRestored.get());
        verify(publications, never()).commit(any(), any(), any(), any());
        verify(coordinator, never()).fail(any(), any(), any(), any());
    }

    @Test
    void lostLeaseDoesNotRaiseASecondFailureWhileRecordingRetry() {
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
        doThrow(new IllegalStateException("lease expired"))
                .when(coordinator)
                .fail(any(), any(), any(), any());
        GraphIndexingProcessor processor = new GraphIndexingProcessor(
                coordinator,
                publications,
                extractors,
                new StaticListableBeanFactory().getBeanProvider(EmbeddingModel.class),
                routes,
                properties,
                GraphRagEventSink.NO_OP);

        assertDoesNotThrow(processor::processNext);

        verify(publications, never()).commit(any(), any(), any(), any());
    }

    private static ClaimedGraphIndex claim() {
        return claim(List.of(chunk(
                CHUNK_ID, 0, "OrgMemory builds secure retrieval.", null)));
    }

    private static ClaimedGraphIndex claim(List<GraphIndexChunk> chunks) {
        var graphProcessingProfile =
                LightRagGraphProcessingProfiles.current(new ExtractionProfile(
                        "openai",
                        "gpt-test",
                        LightRagExtractionPrompt.VERSION,
                        40,
                        60));
        var graphProcessingProfileRef = new GraphProcessingProfileRef(
                UUID.randomUUID(),
                graphProcessingProfile.canonicalSha256(),
                graphProcessingProfile);
        return new ClaimedGraphIndex(
                JOB_ID,
                ORGANIZATION_ID,
                ASSET_ID,
                SPACE_ID,
                VERSION_ID,
                REVISION_ID,
                ACL_SNAPSHOT_ID,
                1,
                1,
                graphProcessingProfileRef,
                "graph:"
                        + ORGANIZATION_ID
                        + ":"
                        + REVISION_ID
                        + ":1:"
                        + graphProcessingProfile.canonicalSha256(),
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
                chunks);
    }

    private static GraphIndexChunk chunk(
            UUID id,
            int index,
            String content,
            String heading) {
        return new GraphIndexChunk(
                id,
                index,
                content,
                heading,
                content.split("\\s+").length,
                new FloatVector(new float[] {1.0f, 0.0f, 0.0f}));
    }

    private static GraphIndexingProperties properties() {
        return properties(Duration.ofMinutes(5));
    }

    private static GraphIndexingProperties properties(Duration leaseDuration) {
        return properties(leaseDuration, Duration.ofMinutes(2));
    }

    private static GraphIndexingProperties properties(
            Duration leaseDuration, Duration extractionTimeout) {
        return new GraphIndexingProperties(
                false,
                Duration.ofSeconds(3),
                "graph-worker-test",
                leaseDuration,
                extractionTimeout,
                2);
    }

    private static ObjectProvider<EmbeddingModel> provider(EmbeddingModel embeddingModel) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("embeddingModel", embeddingModel);
        return beans.getBeanProvider(EmbeddingModel.class);
    }
}
