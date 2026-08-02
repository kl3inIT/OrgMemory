package com.orgmemory.worker.graph;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.worker.WorkProcessingResult;
import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.knowledge.graph.ClaimedGraphIndex;
import com.orgmemory.core.knowledge.retrieval.EmbeddingDistanceMetric;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef;
import com.orgmemory.core.knowledge.graph.GraphIndexChunk;
import com.orgmemory.core.knowledge.graph.GraphIndexingCoordinator;
import com.orgmemory.core.knowledge.graph.GraphProcessingProfileRef;
import com.orgmemory.graphrag.extraction.LightRagExtractionPrompt;
import com.orgmemory.graphrag.model.ExtractedEntity;
import com.orgmemory.graphrag.model.ExtractedRelation;
import com.orgmemory.graphrag.model.ExtractionDiagnostics;
import com.orgmemory.graphrag.model.ExtractionProfile;
import com.orgmemory.graphrag.model.ExtractionResult;
import com.orgmemory.graphrag.model.ExtractionRoundMetrics;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.observability.GraphRagEventSink;
import com.orgmemory.graphrag.observability.GraphRagTaskDecorator;
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
    void reportsEmptyWhenNoJobCanBeClaimed() {
        GraphIndexingCoordinator coordinator = mock(GraphIndexingCoordinator.class);
        GraphIndexingProperties properties = properties();
        when(coordinator.claimNext(properties.workerId(), properties.leaseDuration()))
                .thenReturn(Optional.empty());
        GraphIndexingProcessor processor = processor(
                coordinator,
                mock(GraphPublicationCommitter.class),
                mock(GraphExtractorFactory.class),
                provider(mock(EmbeddingModel.class)),
                mock(AiRouteResolver.class),
                properties,
                mock(GraphRagEventSink.class));

        assertEquals(WorkProcessingResult.EMPTY_OR_DEFERRED, processor.processNext());
    }

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

        GraphIndexingProcessor processor = processor(
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
        verify(events, times(5)).emit(emitted.capture());
        assertEquals(
                List.of(
                        GraphRagEventSink.Stage.EXTRACT,
                        GraphRagEventSink.Stage.GLEAN,
                        GraphRagEventSink.Stage.MERGE,
                        GraphRagEventSink.Stage.EMBED,
                        GraphRagEventSink.Stage.PUBLISH),
                emitted.getAllValues().stream()
                        .map(GraphRagEventSink.GraphRagEvent::stage)
                        .toList());
    }

    /**
     * Gleaning is the extraction round a profile can disable and a token guard can decline, and
     * folding it into {@code EXTRACT} made gleaning working indistinguishable from gleaning
     * silently not happening. The extractor was already recording which.
     */
    @Test
    void reportsHowManyChunksCompletedGleaningAndWhatItCost() {
        GraphRagEventSink events = mock(GraphRagEventSink.class);
        ClaimedGraphIndex claim = claim(List.of(
                chunk(CHUNK_ID, 0, "OrgMemory builds secure retrieval.", null),
                chunk(SECOND_CHUNK_ID, 1, "OrgMemory also builds retrieval.", null)));
        java.util.concurrent.atomic.AtomicInteger call =
                new java.util.concurrent.atomic.AtomicInteger();

        processorFor(claim, events, request -> extraction(
                request,
                // Only the first chunk gleans; the second is declined by the token guard, which
                // is the case a folded-in stage could not distinguish from gleaning being off.
                call.getAndIncrement() == 0
                        ? new ExtractionDiagnostics(
                                List.of(
                                        round(0, Duration.ofMillis(40)),
                                        round(1, Duration.ofMillis(60))),
                                ExtractionDiagnostics.GleaningOutcome.COMPLETED)
                        : new ExtractionDiagnostics(
                                List.of(round(0, Duration.ofMillis(35))),
                                ExtractionDiagnostics.GleaningOutcome
                                        .SKIPPED_TOKEN_LIMIT)))
                .processNext();

        GraphRagEventSink.GraphRagEvent glean = capturedStage(
                events, GraphRagEventSink.Stage.GLEAN);
        assertEquals(2, glean.inputCount(), "both chunks were eligible");
        assertEquals(1, glean.outputCount(), "only one completed a gleaning round");
        assertEquals(
                Duration.ofMillis(60),
                glean.duration(),
                "the first round is EXTRACT's, so only the second round's time is gleaning's cost");
        assertEquals(JOB_ID, glean.operationId(), "the stage belongs to the job that ran it");
    }

    @Test
    void reportsNoGleaningStageWhenTheProfileTurnsItOff() {
        GraphRagEventSink events = mock(GraphRagEventSink.class);
        ClaimedGraphIndex claim = claim(
                List.of(chunk(CHUNK_ID, 0, "OrgMemory builds secure retrieval.", null)),
                new ExtractionProfile(
                        "openai",
                        "gpt-test",
                        LightRagExtractionPrompt.VERSION,
                        40,
                        60,
                        List.of("PRODUCT", "CAPABILITY"),
                        List.of(),
                        0,
                        24_000,
                        256));

        processorFor(claim, events, request -> extraction(
                request,
                ExtractionDiagnostics.notProfiled()))
                .processNext();

        ArgumentCaptor<GraphRagEventSink.GraphRagEvent> emitted =
                ArgumentCaptor.forClass(GraphRagEventSink.GraphRagEvent.class);
        verify(events, atLeastOnce()).emit(emitted.capture());
        assertTrue(
                emitted.getAllValues().stream().noneMatch(event ->
                        event.stage() == GraphRagEventSink.Stage.GLEAN),
                "a zero-valued series would claim a round that was never configured to run");
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

        GraphIndexingProcessor processor = processor(
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
        GraphIndexingProcessor processor = processor(
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
        GraphIndexingProcessor processor = processor(
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
        GraphIndexingProcessor processor = processor(
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
        GraphIndexingProcessor processor = processor(
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

    private static GraphIndexingProcessor processor(
            GraphIndexingCoordinator coordinator,
            GraphPublicationCommitter publications,
            GraphExtractorFactory extractors,
            ObjectProvider<EmbeddingModel> embeddingModels,
            AiRouteResolver routes,
            GraphIndexingProperties properties,
            GraphRagEventSink events) {
        return new GraphIndexingProcessor(
                coordinator,
                publications,
                extractors,
                embeddingModels,
                routes,
                properties,
                events,
                GraphRagTaskDecorator.NONE,
                null);
    }

    /**
     * Wires a processor whose only interesting variable is what the extractor reports, so a test
     * about gleaning does not have to restate the embedding and publication setup.
     */
    private static GraphIndexingProcessor processorFor(
            ClaimedGraphIndex claim,
            GraphRagEventSink events,
            EntityRelationExtractor extractor) {
        GraphIndexingCoordinator coordinator = mock(GraphIndexingCoordinator.class);
        GraphExtractorFactory extractors = mock(GraphExtractorFactory.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        AiRouteResolver routes = mock(AiRouteResolver.class);
        GraphIndexingProperties properties = properties();
        when(coordinator.claimNext(properties.workerId(), properties.leaseDuration()))
                .thenReturn(Optional.of(claim));
        when(routes.resolve(AiWorkload.GRAPH_EXTRACTION))
                .thenReturn(new AiRoute("openai", "gpt-5.6-sol"));
        when(routes.resolve(AiWorkload.DOCUMENT_EMBEDDING))
                .thenReturn(new AiRoute("openai", "text-embedding-3-large"));
        when(extractors.create(new AiRoute("openai", "gpt-test")))
                .thenReturn(extractor);
        when(embeddingModel.embed(
                        anyList(), isNull(), any(TokenCountBatchingStrategy.class)))
                .thenAnswer(invocation -> ((List<Document>) invocation.getArgument(0))
                        .stream()
                        .map(ignored -> new float[] {1.0f, 0.0f, 0.0f})
                        .toList());
        return processor(
                coordinator,
                mock(GraphPublicationCommitter.class),
                extractors,
                provider(embeddingModel),
                routes,
                properties,
                events);
    }

    private static ExtractionResult extraction(
            com.orgmemory.graphrag.model.ExtractionRequest request,
            ExtractionDiagnostics diagnostics) {
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
                        0.96)),
                diagnostics);
    }

    private static ExtractionRoundMetrics round(int round, Duration elapsed) {
        return new ExtractionRoundMetrics(round, 100, 100, 20, elapsed);
    }

    private static GraphRagEventSink.GraphRagEvent capturedStage(
            GraphRagEventSink events,
            GraphRagEventSink.Stage stage) {
        ArgumentCaptor<GraphRagEventSink.GraphRagEvent> emitted =
                ArgumentCaptor.forClass(GraphRagEventSink.GraphRagEvent.class);
        verify(events, atLeastOnce()).emit(emitted.capture());
        return emitted.getAllValues().stream()
                .filter(event -> event.stage() == stage)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + stage + " event was emitted"));
    }

    private static ClaimedGraphIndex claim(List<GraphIndexChunk> chunks) {
        return claim(
                chunks,
                new ExtractionProfile(
                        "openai",
                        "gpt-test",
                        LightRagExtractionPrompt.VERSION,
                        40,
                        60));
    }

    private static ClaimedGraphIndex claim(
            List<GraphIndexChunk> chunks,
            ExtractionProfile extractionProfile) {
        var graphProcessingProfile =
                LightRagGraphProcessingProfiles.current(extractionProfile);
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
                "graph-worker-test",
                leaseDuration,
                extractionTimeout,
                2,
                null,
                null);
    }

    private static ObjectProvider<EmbeddingModel> provider(EmbeddingModel embeddingModel) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("embeddingModel", embeddingModel);
        return beans.getBeanProvider(EmbeddingModel.class);
    }
}
