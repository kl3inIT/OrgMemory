package com.orgmemory.core.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.BatchAuthorizationResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.retrieval.EmbeddingDistanceMetric;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRegistry;
import com.orgmemory.core.knowledge.retrieval.KnowledgeEmbeddingProperties;
import com.orgmemory.core.knowledge.search.SecureKnowledgeSearchResult;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.observability.GraphRagEventSink;
import com.orgmemory.graphrag.observability.GraphRagTaskDecorator;
import com.orgmemory.graphrag.query.ContextTokenUsage;
import com.orgmemory.graphrag.query.KeywordPlan;
import com.orgmemory.graphrag.query.LightRagGrounding;
import com.orgmemory.graphrag.query.LightRagGroundingAssembler;
import com.orgmemory.graphrag.query.LightRagPreparedQuery;
import com.orgmemory.graphrag.query.LightRagQueryEngine;
import com.orgmemory.graphrag.query.LightRagQueryMode;
import com.orgmemory.graphrag.query.LightRagQueryRequest;
import com.orgmemory.graphrag.query.LightRagQueryResult;
import com.orgmemory.graphrag.query.QueryOutputMode;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class GraphRagKnowledgeRetrievalServiceTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID SPACE_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000003");
    private static final UUID ASSET_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID REVISION_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000005");
    private static final UUID CHUNK_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000006");
    private static final UUID ENTITY_CHUNK_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000009");
    private static final UUID RELATION_CHUNK_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000010");
    private static final UUID ACL_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000007");
    private static final UUID PROFILE_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000008");
    private static final UUID SECOND_SPACE_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000011");
    private static final UUID SECOND_ASSET_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000012");
    private static final UUID THIRD_SPACE_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000013");
    private static final UUID THIRD_ASSET_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000014");
    private static final String MODEL_ID = "model-v1";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void multipleSpacesPrepareOneLogicalQueryBeforeSnapshotRetrieval() {
        CurrentActor actor = new CurrentActor(
                USER_ID,
                ORGANIZATION_ID,
                null,
                "User",
                "user@example.test");
        PermissionAuditService audit =
                mock(PermissionAuditService.class);
        KnowledgeEvidenceScopeResolver scopes =
                mock(KnowledgeEvidenceScopeResolver.class);
        ResolvedKnowledgeEvidenceScope multiSpace =
                new ResolvedKnowledgeEvidenceScope(
                        ORGANIZATION_ID,
                        USER_ID,
                        null,
                        false,
                        MODEL_ID,
                        NOW,
                        Map.of(
                                SPACE_ID, Set.of(ASSET_ID),
                                SECOND_SPACE_ID, Set.of(SECOND_ASSET_ID)),
                        Map.of(
                                SPACE_ID, 1L,
                                SECOND_SPACE_ID, 1L));
        when(scopes.resolve(actor, MODEL_ID))
                .thenReturn(multiSpace);
        LightRagQueryEngine engine = mock(LightRagQueryEngine.class);
        LightRagPreparedQuery prepared =
                preparedQueryPlan();
        when(engine.prepare(any())).thenReturn(prepared);
        CountDownLatch bothSpacesStarted = new CountDownLatch(2);
        when(engine.executePrepared(any(), any())).thenAnswer(invocation -> {
            bothSpacesStarted.countDown();
            assertTrue(
                    bothSpacesStarted.await(2, TimeUnit.SECONDS),
                    "space retrievals should run concurrently");
            return noResults();
        });
        GraphRagEventSink events = mock(GraphRagEventSink.class);

        GraphRagKnowledgeRetrievalService service = service(
                scopes,
                mock(RelationshipAuthorizationSetPort.class),
                mock(SecureKnowledgeRetrievalStore.class),
                engine,
                GraphRagRetrievalPolicy.defaults(),
                audit,
                events);

        SecureKnowledgeSearchResult result = service.search(
                actor,
                "What is the leave policy?",
                10,
                "request-multi-space");

        assertEquals(List.of(), result.evidence());
        verify(engine).prepare(any());
        verify(engine, times(2))
                .executePrepared(any(), any());
        ArgumentCaptor<GraphRagEventSink.GraphRagEvent> captured =
                ArgumentCaptor.forClass(GraphRagEventSink.GraphRagEvent.class);
        verify(events, atLeastOnce()).emit(captured.capture());
        GraphRagEventSink.GraphRagEvent keywordStage =
                captured.getAllValues().stream()
                        .filter(event -> event.stage()
                                == GraphRagEventSink.Stage.PREPARE_QUERY)
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                GraphRagEventSink.CacheStatus.MISS,
                keywordStage.cacheStatus());
        assertTrue(captured.getAllValues().stream().anyMatch(event ->
                event.stage() == GraphRagEventSink.Stage.EMBED));
        List<GraphRagEventSink.GraphRagEvent> snapshotStages =
                captured.getAllValues().stream()
                .filter(event -> event.stage()
                        == GraphRagEventSink.Stage.RETRIEVE_SNAPSHOT)
                .toList();
        assertEquals(2, snapshotStages.size());
        assertTrue(snapshotStages.stream()
                .allMatch(event -> event.scopeFingerprint() != null));
    }

    @Test
    void acquiresAdmissionPermitBeforeExecutingTheSnapshotStoreQuery() {
        CurrentActor actor = actor();
        PermissionAuditService audit = mock(PermissionAuditService.class);
        KnowledgeEvidenceScopeResolver scopes =
                mock(KnowledgeEvidenceScopeResolver.class);
        when(scopes.resolve(actor, MODEL_ID))
                .thenReturn(scope(Set.of(ASSET_ID), 1L));
        RetrievalAdmissionControl admission =
                new RetrievalAdmissionControl(1);
        LightRagQueryEngine engine = mock(LightRagQueryEngine.class);
        LightRagPreparedQuery prepared = preparedQueryPlan();
        when(engine.prepare(any())).thenReturn(prepared);
        when(engine.executePrepared(any(), any())).thenAnswer(invocation -> {
            assertEquals(
                    0,
                    admission.availablePermits(),
                    "the storage-query boundary must not be entered before admission");
            return noResults();
        });

        service(
                        scopes,
                        mock(RelationshipAuthorizationSetPort.class),
                        mock(SecureKnowledgeRetrievalStore.class),
                        engine,
                        policy(4, 1),
                        audit,
                        mock(GraphRagEventSink.class),
                        admission)
                .search(
                        actor,
                        "What is the leave policy?",
                        10,
                        "request-admission-before-checkout");

        verify(engine).executePrepared(any(), any());
        assertEquals(1, admission.availablePermits());
    }

    @Test
    void continuousAdmissionDoesNotWaitForAnEarlierSnapshotBatch()
            throws InterruptedException {
        CurrentActor actor = actor();
        PermissionAuditService audit = mock(PermissionAuditService.class);
        KnowledgeEvidenceScopeResolver scopes =
                mock(KnowledgeEvidenceScopeResolver.class);
        when(scopes.resolve(actor, MODEL_ID))
                .thenReturn(threeSpaceScope());
        LightRagQueryEngine engine = mock(LightRagQueryEngine.class);
        LightRagPreparedQuery prepared = preparedQueryPlan();
        when(engine.prepare(any())).thenReturn(prepared);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch thirdStarted = new CountDownLatch(1);
        when(engine.executePrepared(any(), any())).thenAnswer(invocation -> {
            LightRagQueryRequest request = invocation.getArgument(0);
            Set<UUID> assets = request.scope().authorizedAssetIds();
            if (assets.contains(ASSET_ID)) {
                firstStarted.countDown();
                assertTrue(releaseFirst.await(2, TimeUnit.SECONDS));
            } else if (assets.contains(THIRD_ASSET_ID)) {
                thirdStarted.countDown();
            }
            return noResults();
        });
        GraphRagKnowledgeRetrievalService service = service(
                scopes,
                mock(RelationshipAuthorizationSetPort.class),
                mock(SecureKnowledgeRetrievalStore.class),
                engine,
                policy(2, 2),
                audit,
                mock(GraphRagEventSink.class));

        var result = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                service.search(
                        actor,
                        "What is the leave policy?",
                        10,
                        "request-continuous-admission"));

        try {
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            assertTrue(
                    thirdStarted.await(2, TimeUnit.SECONDS),
                    "the next space should enter as soon as a permit is free");
        } finally {
            releaseFirst.countDown();
        }
        assertEquals(List.of(), result.join().evidence());
    }

    @Test
    void snapshotFailureCancelsOutstandingContinuouslyAdmittedTasks()
            throws InterruptedException {
        CurrentActor actor = actor();
        PermissionAuditService audit = mock(PermissionAuditService.class);
        KnowledgeEvidenceScopeResolver scopes =
                mock(KnowledgeEvidenceScopeResolver.class);
        when(scopes.resolve(actor, MODEL_ID))
                .thenReturn(threeSpaceScope());
        LightRagQueryEngine engine = mock(LightRagQueryEngine.class);
        LightRagPreparedQuery prepared = preparedQueryPlan();
        when(engine.prepare(any())).thenReturn(prepared);
        CountDownLatch blockersStarted = new CountDownLatch(2);
        CountDownLatch neverReleased = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(2);
        when(engine.executePrepared(any(), any())).thenAnswer(invocation -> {
            LightRagQueryRequest request = invocation.getArgument(0);
            if (request.scope().authorizedAssetIds().contains(SECOND_ASSET_ID)) {
                assertTrue(blockersStarted.await(2, TimeUnit.SECONDS));
                throw new IllegalStateException("snapshot failed");
            }
            blockersStarted.countDown();
            try {
                neverReleased.await();
                return noResults();
            } catch (InterruptedException cancelled) {
                interrupted.countDown();
                throw cancelled;
            }
        });
        GraphRagKnowledgeRetrievalService service = service(
                scopes,
                mock(RelationshipAuthorizationSetPort.class),
                mock(SecureKnowledgeRetrievalStore.class),
                engine,
                policy(2, 3),
                audit,
                mock(GraphRagEventSink.class));

        assertTimeoutPreemptively(
                Duration.ofSeconds(3),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> service.search(
                                actor,
                                "What is the leave policy?",
                                10,
                                "request-fail-closed-cancellation")));
        assertTrue(
                interrupted.await(2, TimeUnit.SECONDS),
                "every outstanding snapshot task must be interrupted");
    }

    @Test
    void retrievalAdmissionControlUsesFairQueueing() {
        assertTrue(new RetrievalAdmissionControl(4).fair());
    }

    @Test
    void retrievalObservationRunsKeywordAndRawQueryPathsWithoutAnswerGeneration() {
        CurrentActor actor = actor();
        PermissionAuditService audit = mock(PermissionAuditService.class);
        KnowledgeEvidenceScopeResolver scopes = mock(KnowledgeEvidenceScopeResolver.class);
        ResolvedKnowledgeEvidenceScope allowed = scope(Set.of(ASSET_ID), 1L);
        when(scopes.resolve(actor, MODEL_ID)).thenReturn(allowed);
        LightRagGrounding grounding = grounding();
        LightRagGroundingAssembler.PreparedGrounding preparedGrounding = prepared(grounding);
        LightRagQueryEngine engine = mock(LightRagQueryEngine.class);
        LightRagPreparedQuery keywordPrepared = preparedQueryPlan();
        LightRagPreparedQuery bypassPrepared = mock(LightRagPreparedQuery.class);
        when(bypassPrepared.keywordPlanningDuration()).thenReturn(Duration.ZERO);
        when(bypassPrepared.embeddingDuration()).thenReturn(Duration.ofMillis(2));
        when(bypassPrepared.keywordCacheStatus()).thenReturn(GraphRagEventSink.CacheStatus.BYPASS);
        when(bypassPrepared.keywords()).thenReturn(KeywordPlan.empty(KeywordPlan.Source.MODEL));
        when(bypassPrepared.embeddingInputs()).thenReturn(List.of("query"));
        when(engine.prepare(any())).thenAnswer(invocation -> {
            LightRagQueryRequest request = invocation.getArgument(0);
            return request.options().mode() == LightRagQueryMode.MIX
                    ? keywordPrepared
                    : bypassPrepared;
        });
        when(engine.executePrepared(any(), any())).thenReturn(queryResult(
                allowed.forKnowledgeSpace(SPACE_ID).authorizationFingerprint(),
                grounding,
                false,
                false));
        when(engine.consolidateGrounding(any(), any(), any())).thenReturn(preparedGrounding);
        when(engine.renderGrounding(any(), any(), any())).thenReturn(preparedGrounding);
        RelationshipAuthorizationSetPort finalAuthorization =
                mock(RelationshipAuthorizationSetPort.class);
        ResourceRef asset = ResourceRef.of(ORGANIZATION_ID, "knowledge_asset", ASSET_ID);
        when(finalAuthorization.batchCheck(any())).thenReturn(
                BatchAuthorizationResult.resolved(
                        Map.of(asset, AuthorizationDecision.allow(MODEL_ID)),
                        MODEL_ID));
        RecordingRecheckedStore canonical = new RecordingRecheckedStore(List.of(
                candidate(ENTITY_CHUNK_ID),
                candidate(RELATION_CHUNK_ID),
                candidate(CHUNK_ID)));
        GraphRagKnowledgeRetrievalService service = service(
                scopes,
                finalAuthorization,
                canonical,
                engine,
                GraphRagRetrievalPolicy.defaults(),
                audit,
                mock(GraphRagEventSink.class));

        GraphRagKnowledgeRetrievalService.RetrievalObservation observation = service.observe(
                actor,
                "Am I reimbursed during probation?",
                "request-observation");

        ArgumentCaptor<LightRagQueryRequest> requests =
                ArgumentCaptor.forClass(LightRagQueryRequest.class);
        verify(engine, times(2)).prepare(requests.capture());
        assertEquals(
                List.of(LightRagQueryMode.MIX, LightRagQueryMode.NAIVE),
                requests.getAllValues().stream()
                        .map(request -> request.options().mode())
                        .toList());
        assertEquals(60, requests.getAllValues().get(0).options().chunkTopK());
        assertEquals(40, requests.getAllValues().get(1).options().chunkTopK());
        assertTrue(requests.getAllValues().stream().allMatch(request ->
                request.options().outputMode() == QueryOutputMode.CONTEXT));
        verify(audit).recordAll(argThat(commands -> commands.stream().allMatch(command ->
                "request-observation-keyword".equals(command.requestId()))));
        verify(audit).recordAll(argThat(commands -> commands.stream().allMatch(command ->
                "request-observation-bypass".equals(command.requestId()))));
        assertEquals(List.of("leave"), observation.keywordPlan().highLevel());
        assertEquals(List.of("policy"), observation.keywordPlan().lowLevel());
        assertEquals("model", observation.keywordPlan().source());
        assertEquals(3, observation.keywordSeededDocuments().size());
        assertEquals(3, observation.bypassDocuments().size());
    }

    @Test
    void emptyAuthorizedScopeReturnsAllowedEmptyWithoutFinalBatchCheck() {
        CurrentActor actor = actor();
        PermissionAuditService audit = mock(PermissionAuditService.class);
        KnowledgeEvidenceScopeResolver scopes =
                mock(KnowledgeEvidenceScopeResolver.class);
        when(scopes.resolve(actor, MODEL_ID))
                .thenReturn(scope(Set.of(), 0L));
        RelationshipAuthorizationSetPort finalAuthorization =
                mock(RelationshipAuthorizationSetPort.class);

        SecureKnowledgeSearchResult result = service(
                        scopes,
                        finalAuthorization,
                        mock(SecureKnowledgeRetrievalStore.class),
                        mock(LightRagQueryEngine.class),
                        GraphRagRetrievalPolicy.defaults(),
                        audit,
                        mock(GraphRagEventSink.class))
                .search(
                        actor,
                        "What is the leave policy?",
                        10,
                        "request-empty");

        assertEquals(List.of(), result.evidence());
        verify(finalAuthorization, never()).batchCheck(any());
        assertAuditReason(audit, "NO_AUTHORIZED_KNOWLEDGE_ASSETS");
    }

    @Test
    void multipleSpacesDoNotInvokePerSpaceReranking() {
        CurrentActor actor = new CurrentActor(
                USER_ID,
                ORGANIZATION_ID,
                null,
                "User",
                "user@example.test");
        PermissionAuditService audit =
                mock(PermissionAuditService.class);
        KnowledgeEvidenceScopeResolver scopes =
                mock(KnowledgeEvidenceScopeResolver.class);
        ResolvedKnowledgeEvidenceScope multiSpace =
                new ResolvedKnowledgeEvidenceScope(
                        ORGANIZATION_ID,
                        USER_ID,
                        null,
                        false,
                        MODEL_ID,
                        NOW,
                        Map.of(
                                SPACE_ID, Set.of(ASSET_ID),
                                SECOND_SPACE_ID, Set.of(SECOND_ASSET_ID)),
                        Map.of(
                                SPACE_ID, 1L,
                                SECOND_SPACE_ID, 1L));
        when(scopes.resolve(actor, MODEL_ID))
                .thenReturn(multiSpace);
        LightRagQueryEngine engine = mock(LightRagQueryEngine.class);
        GraphRagKnowledgeRetrievalService service = service(
                scopes,
                mock(RelationshipAuthorizationSetPort.class),
                mock(SecureKnowledgeRetrievalStore.class),
                engine,
                rerankPolicy(),
                audit,
                mock(GraphRagEventSink.class));

        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> service.search(
                        actor,
                        "What is the leave policy?",
                        10,
                        "request-multi-space-rerank"));

        verify(engine, never()).prepare(any());
        verify(engine, never()).executePrepared(any(), any());
    }

    @Test
    void revocationBetweenRetrievalAndCitationCausesAFullRetryWithoutEgress() {
        CurrentActor actor = new CurrentActor(
                USER_ID,
                ORGANIZATION_ID,
                null,
                "User",
                "user@example.test");
        RelationshipAuthorizationPort entry =
                mock(RelationshipAuthorizationPort.class);
        when(entry.check(any()))
                .thenReturn(AuthorizationDecision.allow(MODEL_ID));
        PermissionAuditService audit =
                mock(PermissionAuditService.class);
        KnowledgeEvidenceScopeResolver scopes =
                mock(KnowledgeEvidenceScopeResolver.class);
        ResolvedKnowledgeEvidenceScope allowed = scope(Set.of(ASSET_ID), 1L);
        ResolvedKnowledgeEvidenceScope revoked = scope(Set.of(), 0L);
        when(scopes.resolve(actor, MODEL_ID))
                .thenReturn(allowed, revoked, revoked);

        ProjectionPublicationStore publications =
                mock(ProjectionPublicationStore.class);
        ProjectionSnapshot snapshot = new ProjectionSnapshot(
                UUID.randomUUID(),
                new ProjectionNamespace(
                        ORGANIZATION_ID,
                        "default",
                        SPACE_ID.toString()),
                1L,
                "manifest-v1",
                Set.of(
                        ProjectionKind.CONTENT,
                        ProjectionKind.VECTOR,
                        ProjectionKind.GRAPH),
                NOW);
        when(publications.current(any()))
                .thenReturn(java.util.Optional.of(snapshot));
        LightRagQueryEngine engine = mock(LightRagQueryEngine.class);
        LightRagPreparedQuery queryPlan = preparedQueryPlan();
        when(engine.prepare(any()))
                .thenReturn(queryPlan);
        LightRagGrounding grounding = grounding();
        LightRagGroundingAssembler.PreparedGrounding prepared =
                prepared(grounding);
        when(engine.executePrepared(any(), any())).thenReturn(queryResult(
                allowed.forKnowledgeSpace(SPACE_ID)
                        .authorizationFingerprint(),
                grounding,
                false,
                false));
        when(engine.consolidateGrounding(any(), any(), any()))
                .thenReturn(prepared);
        EmbeddingProfileRegistry profiles =
                mock(EmbeddingProfileRegistry.class);
        when(profiles.find(any(), any())).thenReturn(java.util.Optional.of(
                new EmbeddingProfileRef(
                        PROFILE_ID,
                        ORGANIZATION_ID,
                        "profile",
                        "openai",
                        "text-embedding-3-large",
                        1536,
                        EmbeddingDistanceMetric.COSINE)));
        RelationshipAuthorizationSetPort finalAuthorization =
                mock(RelationshipAuthorizationSetPort.class);
        NeverRecheckedStore canonical =
                new NeverRecheckedStore();
        GraphRagEventSink events = mock(GraphRagEventSink.class);

        var service = new DefaultGraphRagKnowledgeRetrievalService(
                new KnowledgeSearchAuthorizationService(entry, audit),
                scopes,
                finalAuthorization,
                canonical,
                profiles,
                new KnowledgeEmbeddingProperties(
                        "openai",
                        "text-embedding-3-large",
                        1536),
                publications,
                engine,
                GraphRagRetrievalPolicy.defaults(),
                audit,
                new KnowledgeRetrievalProperties(
                        20,
                        5,
                        5_000,
                        1_000),
                events);

        SecureKnowledgeSearchResult result = service.search(
                actor,
                "What is the leave policy?",
                10,
                "request-1");

        assertEquals(List.of(), result.evidence());
        verify(engine).prepare(any());
        verify(engine).executePrepared(any(), any());
        verify(finalAuthorization, never()).batchCheck(any());
        assertEquals(0, canonical.recheckCount);
        ArgumentCaptor<GraphRagEventSink.GraphRagEvent> captured =
                ArgumentCaptor.forClass(GraphRagEventSink.GraphRagEvent.class);
        verify(events, atLeastOnce()).emit(captured.capture());
        GraphRagEventSink.GraphRagEvent overall = captured.getAllValues()
                .stream()
                .filter(value ->
                        value.stage() == GraphRagEventSink.Stage.RETRIEVE)
                .findFirst()
                .orElseThrow();
        assertEquals(GraphRagEventSink.Outcome.SUCCEEDED, overall.outcome());
        assertEquals(0, overall.outputCount());
        assertNotNull(overall.operationId());
    }

    @Test
    void verifiesTheCompleteGraphGroundingBeforeCreatingTheModelInput() {
        CurrentActor actor = new CurrentActor(
                USER_ID,
                ORGANIZATION_ID,
                null,
                "User",
                "user@example.test");
        PermissionAuditService audit =
                mock(PermissionAuditService.class);
        KnowledgeEvidenceScopeResolver scopes =
                mock(KnowledgeEvidenceScopeResolver.class);
        ResolvedKnowledgeEvidenceScope allowed = scope(Set.of(ASSET_ID), 1L);
        when(scopes.resolve(actor, MODEL_ID))
                .thenReturn(allowed, allowed);
        LightRagGrounding grounding = grounding();
        LightRagGroundingAssembler.PreparedGrounding prepared =
                prepared(grounding);
        LightRagQueryEngine engine = mock(LightRagQueryEngine.class);
        LightRagPreparedQuery queryPlan = preparedQueryPlan();
        when(engine.prepare(any()))
                .thenReturn(queryPlan);
        when(engine.executePrepared(any(), any())).thenReturn(queryResult(
                allowed.forKnowledgeSpace(SPACE_ID)
                        .authorizationFingerprint(),
                grounding,
                true,
                true));
        when(engine.consolidateGrounding(any(), any(), any()))
                .thenReturn(prepared);
        when(engine.renderGrounding(any(), any(), any()))
                .thenReturn(prepared);
        RelationshipAuthorizationSetPort finalAuthorization =
                mock(RelationshipAuthorizationSetPort.class);
        ResourceRef asset = ResourceRef.of(
                ORGANIZATION_ID,
                "knowledge_asset",
                ASSET_ID);
        when(finalAuthorization.batchCheck(any())).thenReturn(
                BatchAuthorizationResult.resolved(
                        Map.of(
                                asset,
                                AuthorizationDecision.allow(MODEL_ID)),
                        MODEL_ID));
        RecordingRecheckedStore canonical =
                new RecordingRecheckedStore(List.of(
                        candidate(ENTITY_CHUNK_ID),
                        candidate(ENTITY_CHUNK_ID),
                        candidate(RELATION_CHUNK_ID),
                        candidate(CHUNK_ID)));
        GraphRagEventSink events = mock(GraphRagEventSink.class);

        var service = service(
                scopes,
                finalAuthorization,
                canonical,
                engine,
                rerankPolicy(),
                audit,
                events);

        SecureKnowledgeSearchResult result = service.search(
                actor,
                "What is the leave policy?",
                10,
                "request-grounding");

        assertEquals(
                Set.of(ENTITY_CHUNK_ID, RELATION_CHUNK_ID, CHUNK_ID),
                Set.copyOf(canonical.recheckedChunkIds));
        assertEquals(3, result.evidence().size());
        assertEquals(3, result.grounding().orElseThrow().evidenceClosureSize());
        assertEquals(
                "verified graph context",
                result.grounding()
                        .orElseThrow()
                        .generationRequest()
                        .systemInstruction());
        ArgumentCaptor<BatchAuthorizationQuery> authorizationQuery =
                ArgumentCaptor.forClass(BatchAuthorizationQuery.class);
        verify(finalAuthorization).batchCheck(authorizationQuery.capture());
        assertEquals(List.of(asset), authorizationQuery.getValue().resources());
        assertTrue(result.evidence().stream().anyMatch(value ->
                value.chunkId().equals(ENTITY_CHUNK_ID)));
        ArgumentCaptor<GraphRagEventSink.GraphRagEvent> graphEvents =
                ArgumentCaptor.forClass(GraphRagEventSink.GraphRagEvent.class);
        verify(events, atLeastOnce())
                .emit(graphEvents.capture());
        GraphRagEventSink.GraphRagEvent fallbackEvent =
                graphEvents.getAllValues()
                        .stream()
                        .filter(value ->
                                value.stage() == GraphRagEventSink.Stage.RERANK)
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                GraphRagEventSink.Outcome.FAILED,
                fallbackEvent.outcome());
        assertEquals(
                "rerank_provider_fallback",
                fallbackEvent.failureCode());
        assertNotNull(fallbackEvent.modelRouteFingerprint());
    }

    /**
     * The assembler has always measured what one answer costs and how much context the budget
     * refused to carry. Nothing published either, so a deployment could not tell an expensive
     * question from a cheap one, nor a whole answer from one that was silently cut to fit.
     */
    @Test
    void contextAssemblyReportsWhatTheAnswerCostAndWhatTheBudgetRefused() {
        CurrentActor actor = new CurrentActor(
                USER_ID,
                ORGANIZATION_ID,
                null,
                "User",
                "user@example.test");
        PermissionAuditService audit = mock(PermissionAuditService.class);
        KnowledgeEvidenceScopeResolver scopes =
                mock(KnowledgeEvidenceScopeResolver.class);
        ResolvedKnowledgeEvidenceScope allowed = scope(Set.of(ASSET_ID), 1L);
        when(scopes.resolve(actor, MODEL_ID)).thenReturn(allowed, allowed);
        LightRagGrounding grounding = grounding();
        LightRagGroundingAssembler.PreparedGrounding prepared =
                prepared(grounding, 4);
        LightRagQueryEngine engine = mock(LightRagQueryEngine.class);
        LightRagPreparedQuery queryPlan = preparedQueryPlan();
        when(engine.prepare(any())).thenReturn(queryPlan);
        when(engine.executePrepared(any(), any())).thenReturn(queryResult(
                allowed.forKnowledgeSpace(SPACE_ID).authorizationFingerprint(),
                grounding,
                true,
                true));
        when(engine.consolidateGrounding(any(), any(), any())).thenReturn(prepared);
        when(engine.renderGrounding(any(), any(), any())).thenReturn(prepared);
        RelationshipAuthorizationSetPort finalAuthorization =
                mock(RelationshipAuthorizationSetPort.class);
        when(finalAuthorization.batchCheck(any())).thenReturn(
                BatchAuthorizationResult.resolved(
                        Map.of(
                                ResourceRef.of(ORGANIZATION_ID, "knowledge_asset", ASSET_ID),
                                AuthorizationDecision.allow(MODEL_ID)),
                        MODEL_ID));
        GraphRagEventSink events = mock(GraphRagEventSink.class);

        service(
                        scopes,
                        finalAuthorization,
                        new RecordingRecheckedStore(List.of(
                                candidate(ENTITY_CHUNK_ID),
                                candidate(RELATION_CHUNK_ID),
                                candidate(CHUNK_ID))),
                        engine,
                        rerankPolicy(),
                        audit,
                        events)
                .search(actor, "What is the leave policy?", 10, "request-tokens");

        ArgumentCaptor<GraphRagEventSink.GraphRagEvent> captured =
                ArgumentCaptor.forClass(GraphRagEventSink.GraphRagEvent.class);
        verify(events, atLeastOnce()).emit(captured.capture());
        GraphRagEventSink.TokenUsage usage = captured.getAllValues().stream()
                .filter(value ->
                        value.stage() == GraphRagEventSink.Stage.ASSEMBLE_CONTEXT)
                .findFirst()
                .orElseThrow()
                .tokenUsage();

        assertNotNull(usage, "context assembly is the stage whose cost is measured in tokens");
        assertEquals(45, usage.promptTokens());
        assertEquals(4, usage.droppedContributions());
        assertTrue(usage.truncated());
        assertEquals(
                GraphRagRetrievalPolicy.defaults()
                        .contextOptions(10)
                        .contextBudget()
                        .maximumInputTokens(),
                usage.budgetTokens(),
                "headroom is only readable if the ceiling travels with the measurement");
        assertTrue(
                captured.getAllValues().stream()
                        .filter(value ->
                                value.stage() != GraphRagEventSink.Stage.ASSEMBLE_CONTEXT)
                        .allMatch(value -> value.tokenUsage() == null),
                "no other stage measures tokens, and a zero there would read as a measured zero");
    }

    @Test
    void authorizationModelMismatchCannotReachTheVerifiedRenderer() {
        CurrentActor actor = new CurrentActor(
                USER_ID,
                ORGANIZATION_ID,
                null,
                "User",
                "user@example.test");
        PermissionAuditService audit =
                mock(PermissionAuditService.class);
        KnowledgeEvidenceScopeResolver scopes =
                mock(KnowledgeEvidenceScopeResolver.class);
        ResolvedKnowledgeEvidenceScope allowed = scope(Set.of(ASSET_ID), 1L);
        when(scopes.resolve(actor, MODEL_ID))
                .thenReturn(allowed, allowed);
        LightRagGrounding grounding = grounding();
        LightRagQueryEngine engine = mock(LightRagQueryEngine.class);
        LightRagPreparedQuery queryPlan = preparedQueryPlan();
        when(engine.prepare(any()))
                .thenReturn(queryPlan);
        when(engine.executePrepared(any(), any())).thenReturn(queryResult(
                allowed.forKnowledgeSpace(SPACE_ID)
                        .authorizationFingerprint(),
                grounding,
                false,
                false));
        when(engine.consolidateGrounding(any(), any(), any()))
                .thenReturn(prepared(grounding));
        RelationshipAuthorizationSetPort finalAuthorization =
                mock(RelationshipAuthorizationSetPort.class);
        ResourceRef asset = ResourceRef.of(
                ORGANIZATION_ID,
                "knowledge_asset",
                ASSET_ID);
        when(finalAuthorization.batchCheck(any())).thenReturn(
                BatchAuthorizationResult.resolved(
                        Map.of(
                                asset,
                                AuthorizationDecision.allow("model-v2")),
                        "model-v2"));
        NeverRecheckedStore canonical =
                new NeverRecheckedStore();
        GraphRagKnowledgeRetrievalService service = service(
                scopes,
                finalAuthorization,
                canonical,
                engine,
                GraphRagRetrievalPolicy.defaults(),
                audit,
                mock(GraphRagEventSink.class));

        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> service.search(
                        actor,
                        "What is the leave policy?",
                        10,
                        "request-model-mismatch"));

        verify(engine, never()).renderGrounding(any(), any(), any());
        assertEquals(0, canonical.recheckCount);
    }

    @Test
    void mixedAllowAndDenyRejectsTheEntireGraphClosure() {
        AuthorizationFixture fixture = authorizationFixture(
                scope(Set.of(ASSET_ID, SECOND_ASSET_ID), 1L),
                groundingAcrossAssets());
        when(fixture.authorization.batchCheck(any())).thenReturn(
                BatchAuthorizationResult.resolved(
                        Map.of(
                                resource(ASSET_ID),
                                AuthorizationDecision.allow(MODEL_ID),
                                resource(SECOND_ASSET_ID),
                                AuthorizationDecision.deny(
                                        "RELATIONSHIP_DENIED",
                                        MODEL_ID)),
                        MODEL_ID));

        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                fixture::search);

        assertAuditReason(
                fixture.audit,
                "FINAL_OPENFGA_RECHECK_DENIED");
        verify(fixture.engine, never()).renderGrounding(any(), any(), any());
    }

    @Test
    void missingAndPerDecisionModelMismatchUseFinalRecheckReason() {
        for (BatchAuthorizationResult result : List.of(
                BatchAuthorizationResult.resolved(
                        Map.of(resource(SECOND_ASSET_ID),
                                AuthorizationDecision.allow(MODEL_ID)),
                        MODEL_ID),
                BatchAuthorizationResult.resolved(
                        Map.of(resource(ASSET_ID),
                                AuthorizationDecision.allow("model-v2")),
                        MODEL_ID))) {
            AuthorizationFixture fixture = authorizationFixture(
                    scope(Set.of(ASSET_ID), 1L),
                    grounding());
            when(fixture.authorization.batchCheck(any())).thenReturn(result);

            assertThrows(
                    KnowledgeRetrievalUnavailableException.class,
                    fixture::search);

            assertAuditReason(
                    fixture.audit,
                    "FINAL_OPENFGA_RECHECK_DENIED");
            verify(fixture.engine, never())
                    .renderGrounding(any(), any(), any());
        }
    }

    @Test
    void outerBatchModelMismatchKeepsBatchResolvedAuditReason() {
        AuthorizationFixture fixture = authorizationFixture(
                scope(Set.of(ASSET_ID), 1L),
                grounding());
        when(fixture.authorization.batchCheck(any())).thenReturn(
                BatchAuthorizationResult.resolved(
                        Map.of(resource(ASSET_ID),
                                AuthorizationDecision.allow("model-v2")),
                        "model-v2"));

        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                fixture::search);

        assertAuditReason(
                fixture.audit,
                "BATCH_AUTHORIZATION_RESOLVED");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "OPENFGA_TIMEOUT",
            "OPENFGA_UNAVAILABLE",
            "OPENFGA_INTERRUPTED"
    })
    void finalBatchProviderFailureReasonIsPreserved(String reasonCode) {
        AuthorizationFixture fixture = authorizationFixture(
                scope(Set.of(ASSET_ID), 1L),
                grounding());
        when(fixture.authorization.batchCheck(any())).thenReturn(
                BatchAuthorizationResult.indeterminate(reasonCode, MODEL_ID));

        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                fixture::search);

        assertAuditReason(fixture.audit, reasonCode);
    }

    private static ResolvedKnowledgeEvidenceScope scope(
            Set<UUID> assets,
            long aclGeneration) {
        Map<UUID, Set<UUID>> bySpace = assets.isEmpty()
                ? Map.of()
                : Map.of(SPACE_ID, assets);
        Map<UUID, Long> generations = assets.isEmpty()
                ? Map.of()
                : Map.of(SPACE_ID, aclGeneration);
        return new ResolvedKnowledgeEvidenceScope(
                ORGANIZATION_ID,
                USER_ID,
                null,
                false,
                MODEL_ID,
                NOW,
                bySpace,
                generations);
    }

    private static CurrentActor actor() {
        return new CurrentActor(
                USER_ID,
                ORGANIZATION_ID,
                null,
                "User",
                "user@example.test");
    }

    private static ResourceRef resource(UUID assetId) {
        return ResourceRef.of(
                ORGANIZATION_ID,
                "knowledge_asset",
                assetId);
    }

    private static void assertAuditReason(
            PermissionAuditService audit,
            String reasonCode) {
        ArgumentCaptor<PermissionAuditCommand> recorded =
                ArgumentCaptor.forClass(PermissionAuditCommand.class);
        verify(audit).record(recorded.capture());
        assertEquals(reasonCode, recorded.getValue().reasonCode());
    }

    private static AuthorizationFixture authorizationFixture(
            ResolvedKnowledgeEvidenceScope allowed,
            LightRagGrounding grounding) {
        CurrentActor actor = actor();
        PermissionAuditService audit = mock(PermissionAuditService.class);
        KnowledgeEvidenceScopeResolver scopes =
                mock(KnowledgeEvidenceScopeResolver.class);
        when(scopes.resolve(actor, MODEL_ID)).thenReturn(allowed, allowed);
        LightRagQueryEngine engine = mock(LightRagQueryEngine.class);
        LightRagPreparedQuery queryPlan = preparedQueryPlan();
        when(engine.prepare(any())).thenReturn(queryPlan);
        when(engine.executePrepared(any(), any())).thenReturn(queryResult(
                allowed.forKnowledgeSpace(SPACE_ID)
                        .authorizationFingerprint(),
                grounding,
                false,
                false));
        when(engine.consolidateGrounding(any(), any(), any()))
                .thenReturn(prepared(grounding));
        RelationshipAuthorizationSetPort authorization =
                mock(RelationshipAuthorizationSetPort.class);
        GraphRagKnowledgeRetrievalService service = service(
                scopes,
                authorization,
                new NeverRecheckedStore(),
                engine,
                GraphRagRetrievalPolicy.defaults(),
                audit,
                mock(GraphRagEventSink.class));
        return new AuthorizationFixture(
                actor,
                audit,
                authorization,
                engine,
                service);
    }

    private static ProjectionSnapshot snapshot() {
        return new ProjectionSnapshot(
                UUID.randomUUID(),
                new ProjectionNamespace(
                        ORGANIZATION_ID,
                        "default",
                        SPACE_ID.toString()),
                1L,
                "manifest-v1",
                Set.of(
                        ProjectionKind.CONTENT,
                        ProjectionKind.VECTOR,
                        ProjectionKind.GRAPH),
                NOW);
    }

    private static LightRagQueryResult queryResult(
            String authorizationFingerprint,
            LightRagGrounding grounding,
            boolean rerankAttempted,
            boolean rerankFallback) {
        return new LightRagQueryResult(
                LightRagQueryResult.Status.SUCCESS,
                "authorized context",
                "",
                new LightRagQueryResult.NoAnswer(),
                List.of(new LightRagQueryResult.Reference(
                        1,
                        evidence(CHUNK_ID),
                        "Leave policy",
                        Map.of("sourceLabel", "Leave policy"))),
                grounding,
                new LightRagQueryResult.Trace(
                        LightRagQueryMode.MIX,
                        KeywordPlan.model(
                                List.of("leave"),
                                List.of("policy")),
                        List.of("query"),
                        1,
                        0,
                        1,
                        1,
                        0,
                        1,
                        rerankAttempted,
                        rerankFallback,
                        rerankAttempted
                                ? Duration.ofMillis(5)
                                : Duration.ZERO,
                        List.of(new LightRagQueryResult.ChunkSignal(
                                CHUNK_ID,
                                LightRagQueryResult.Origin.VECTOR,
                                1,
                                1,
                                0.9,
                                null)),
                        authorizationFingerprint,
                        1L,
                        ""));
    }

    private static LightRagPreparedQuery preparedQueryPlan() {
        LightRagPreparedQuery prepared =
                mock(LightRagPreparedQuery.class);
        when(prepared.keywordPlanningDuration())
                .thenReturn(Duration.ofMillis(3));
        when(prepared.embeddingDuration())
                .thenReturn(Duration.ofMillis(4));
        when(prepared.keywordCacheStatus())
                .thenReturn(GraphRagEventSink.CacheStatus.MISS);
        when(prepared.keywords()).thenReturn(KeywordPlan.model(
                List.of("leave"),
                List.of("policy")));
        when(prepared.embeddingInputs()).thenReturn(List.of("query"));
        return prepared;
    }

    private static LightRagQueryResult noResults() {
        return new LightRagQueryResult(
                LightRagQueryResult.Status.NO_RESULTS,
                "",
                "",
                new LightRagQueryResult.NoAnswer(),
                List.of(),
                new LightRagQueryResult.Trace(
                        LightRagQueryMode.MIX,
                        KeywordPlan.model(
                                List.of("policy"),
                                List.of("leave")),
                        List.of("query"),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        false,
                        false,
                        Duration.ZERO,
                        List.of(),
                        "authorization-fingerprint",
                        1L,
                        "no_authorized_context"));
    }

    private static GraphRagRetrievalPolicy rerankPolicy() {
        GraphRagRetrievalPolicy defaults =
                GraphRagRetrievalPolicy.defaults();
        return new GraphRagRetrievalPolicy(
                defaults.maximumKnowledgeSpaces(),
                defaults.maximumConcurrentSpaces(),
                defaults.retrievalAdmissionPermits(),
                defaults.topK(),
                defaults.chunkTopK(),
                defaults.relatedChunkNumber(),
                defaults.maximumGraphDepth(),
                defaults.maximumEvidenceClosure(),
                defaults.minimumVectorSimilarity(),
                defaults.includeHeadings(),
                new GraphRagRetrievalPolicy.RerankPolicy(
                        true,
                        "test-reranker",
                        0.2),
                defaults.contextBudget());
    }

    private static GraphRagKnowledgeRetrievalService service(
            KnowledgeEvidenceScopeResolver scopes,
            RelationshipAuthorizationSetPort finalAuthorization,
            SecureKnowledgeRetrievalStore canonical,
            LightRagQueryEngine engine,
            GraphRagRetrievalPolicy policy,
            PermissionAuditService audit,
            GraphRagEventSink events) {
        return service(
                scopes,
                finalAuthorization,
                canonical,
                engine,
                policy,
                audit,
                events,
                new RetrievalAdmissionControl(
                        policy.retrievalAdmissionPermits()));
    }

    private static GraphRagKnowledgeRetrievalService service(
            KnowledgeEvidenceScopeResolver scopes,
            RelationshipAuthorizationSetPort finalAuthorization,
            SecureKnowledgeRetrievalStore canonical,
            LightRagQueryEngine engine,
            GraphRagRetrievalPolicy policy,
            PermissionAuditService audit,
            GraphRagEventSink events,
            RetrievalAdmissionControl admission) {
        RelationshipAuthorizationPort entry =
                mock(RelationshipAuthorizationPort.class);
        when(entry.check(any()))
                .thenReturn(AuthorizationDecision.allow(MODEL_ID));
        ProjectionPublicationStore publications =
                mock(ProjectionPublicationStore.class);
        when(publications.current(any()))
                .thenReturn(java.util.Optional.of(snapshot()));
        EmbeddingProfileRegistry profiles =
                mock(EmbeddingProfileRegistry.class);
        when(profiles.find(any(), any())).thenReturn(java.util.Optional.of(
                new EmbeddingProfileRef(
                        PROFILE_ID,
                        ORGANIZATION_ID,
                        "profile",
                        "openai",
                        "text-embedding-3-large",
                        1536,
                        EmbeddingDistanceMetric.COSINE)));
        return new DefaultGraphRagKnowledgeRetrievalService(
                new KnowledgeSearchAuthorizationService(entry, audit),
                scopes,
                finalAuthorization,
                canonical,
                profiles,
                new KnowledgeEmbeddingProperties(
                        "openai",
                        "text-embedding-3-large",
                        1536),
                publications,
                engine,
                policy,
                audit,
                new KnowledgeRetrievalProperties(
                        20,
                        5,
                        5_000,
                        1_000),
                events,
                GraphRagTaskDecorator.NONE,
                admission);
    }

    private static GraphRagRetrievalPolicy policy(
            int maximumConcurrentSpaces,
            int admissionPermits) {
        GraphRagRetrievalPolicy defaults =
                GraphRagRetrievalPolicy.defaults();
        return new GraphRagRetrievalPolicy(
                defaults.maximumKnowledgeSpaces(),
                maximumConcurrentSpaces,
                admissionPermits,
                defaults.topK(),
                defaults.chunkTopK(),
                defaults.relatedChunkNumber(),
                defaults.maximumGraphDepth(),
                defaults.maximumEvidenceClosure(),
                defaults.minimumVectorSimilarity(),
                defaults.includeHeadings(),
                defaults.rerank(),
                defaults.contextBudget());
    }

    private static ResolvedKnowledgeEvidenceScope threeSpaceScope() {
        return new ResolvedKnowledgeEvidenceScope(
                ORGANIZATION_ID,
                USER_ID,
                null,
                false,
                MODEL_ID,
                NOW,
                Map.of(
                        SPACE_ID, Set.of(ASSET_ID),
                        SECOND_SPACE_ID, Set.of(SECOND_ASSET_ID),
                        THIRD_SPACE_ID, Set.of(THIRD_ASSET_ID)),
                Map.of(
                        SPACE_ID, 1L,
                        SECOND_SPACE_ID, 1L,
                        THIRD_SPACE_ID, 1L));
    }

    private static LightRagGrounding grounding() {
        return new LightRagGrounding(
                List.of(new LightRagGrounding.SelectedEntity(
                        UUID.randomUUID(),
                        "probation policy",
                        List.of(new LightRagGrounding.EntityContribution(
                                "POLICY",
                                "A probation policy exists.",
                                evidence(ENTITY_CHUNK_ID),
                                1L,
                                0.9)),
                        0.9,
                        1)),
                List.of(new LightRagGrounding.SelectedRelation(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "employee",
                        "probation policy",
                        List.of(new LightRagGrounding.RelationContribution(
                                "SUBJECT_TO",
                                List.of("probation"),
                                "Employees are subject to probation.",
                                1.0,
                                evidence(RELATION_CHUNK_ID),
                                1L,
                                0.9)),
                        0.8,
                        1)),
                List.of(new LightRagGrounding.SelectedChunk(
                        CHUNK_ID,
                        evidence(CHUNK_ID),
                        1L,
                        "The probation period is 60 days.",
                        Map.of("sourceLabel", "Leave policy"),
                        LightRagQueryResult.Origin.VECTOR,
                        1,
                        1,
                        0.9,
                        null)),
                List.of(new LightRagGrounding.ScopeSnapshot(
                        new ProjectionNamespace(
                                ORGANIZATION_ID,
                                "default",
                                SPACE_ID.toString()),
                        UUID.randomUUID(),
                        1L,
                        "manifest-v1",
                        "fingerprint-v1")),
                new ContextTokenUsage(20, 5, 10, 10),
                10);
    }

    private static LightRagGrounding groundingAcrossAssets() {
        LightRagGrounding original = grounding();
        return new LightRagGrounding(
                original.entities(),
                List.of(new LightRagGrounding.SelectedRelation(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "employee",
                        "probation policy",
                        List.of(new LightRagGrounding.RelationContribution(
                                "SUBJECT_TO",
                                List.of("probation"),
                                "Employees are subject to probation.",
                                1.0,
                                evidence(
                                        RELATION_CHUNK_ID,
                                        SECOND_ASSET_ID),
                                1L,
                                0.9)),
                        0.8,
                        1)),
                original.chunks(),
                original.scopes(),
                original.tokenUsage(),
                original.chunkTokens());
    }

    private static LightRagGroundingAssembler.PreparedGrounding prepared(
            LightRagGrounding grounding) {
        return prepared(grounding, 0);
    }

    private static LightRagGroundingAssembler.PreparedGrounding prepared(
            LightRagGrounding grounding,
            int droppedContributions) {
        List<LightRagQueryResult.Reference> references =
                grounding.evidenceClosure()
                        .stream()
                        .map(value -> new LightRagQueryResult.Reference(
                                grounding.evidenceClosure().indexOf(value) + 1,
                                value.evidence(),
                                "Leave policy",
                                Map.of("sourceLabel", "Leave policy")))
                        .toList();
        return new LightRagGroundingAssembler.PreparedGrounding(
                grounding,
                "verified graph context",
                "verified graph context",
                "verified graph context\n\nWhat is the leave policy?",
                references,
                45,
                droppedContributions);
    }

    private static EvidenceReference evidence(UUID chunkId) {
        return evidence(chunkId, ASSET_ID);
    }

    private static EvidenceReference evidence(
            UUID chunkId,
            UUID assetId) {
        return new EvidenceReference(
                ORGANIZATION_ID,
                assetId,
                REVISION_ID,
                chunkId,
                ACL_ID,
                1L);
    }

    private record AuthorizationFixture(
            CurrentActor actor,
            PermissionAuditService audit,
            RelationshipAuthorizationSetPort authorization,
            LightRagQueryEngine engine,
            GraphRagKnowledgeRetrievalService service) {

        private SecureKnowledgeSearchResult search() {
            return service.search(
                    actor,
                    "What is the leave policy?",
                    10,
                    "request-authorization");
        }
    }

    private static SecureRetrievalCandidate candidate(UUID chunkId) {
        return new SecureRetrievalCandidate(
                ORGANIZATION_ID,
                chunkId,
                ASSET_ID,
                UUID.randomUUID(),
                REVISION_ID,
                "Leave policy",
                "Evidence for " + chunkId,
                "https://example.test/leave-policy",
                1,
                1,
                "Probation",
                0.9,
                ACL_ID,
                ACL_ID,
                MODEL_ID,
                PROFILE_ID,
                1L);
    }

    private static final class NeverRecheckedStore
            extends SecureKnowledgeRetrievalStore {

        private int recheckCount;

        private NeverRecheckedStore() {
            super(null);
        }

        @Override
        public List<SecureRetrievalCandidate> recheck(
                RetrievalScope scope,
                java.util.Collection<UUID> chunkIds) {
            recheckCount++;
            return List.of();
        }
    }

    private static final class RecordingRecheckedStore
            extends SecureKnowledgeRetrievalStore {

        private final List<SecureRetrievalCandidate> candidates;
        private List<UUID> recheckedChunkIds = List.of();

        private RecordingRecheckedStore(
                List<SecureRetrievalCandidate> candidates) {
            super(null);
            this.candidates = List.copyOf(candidates);
        }

        @Override
        public List<SecureRetrievalCandidate> recheck(
                RetrievalScope scope,
                java.util.Collection<UUID> chunkIds) {
            recheckedChunkIds = List.copyOf(chunkIds);
            return candidates;
        }
    }
}
