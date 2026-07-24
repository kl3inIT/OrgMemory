package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.observability.GraphRagEventSink;
import com.orgmemory.graphrag.query.KeywordPlan;
import com.orgmemory.graphrag.query.LightRagQueryEngine;
import com.orgmemory.graphrag.query.LightRagQueryMode;
import com.orgmemory.graphrag.query.LightRagQueryResult;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
    private static final UUID ACL_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000007");
    private static final UUID PROFILE_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000008");
    private static final String MODEL_ID = "model-v1";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

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
        when(engine.execute(any())).thenReturn(queryResult(
                allowed.forKnowledgeSpace(SPACE_ID)
                        .authorizationFingerprint()));
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

        var service = new GraphRagKnowledgeRetrievalService(
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
        verify(engine).execute(any());
        verify(finalAuthorization, never()).batchCheck(any());
        assertEquals(0, canonical.recheckCount);
        ArgumentCaptor<GraphRagEventSink.GraphRagEvent> captured =
                ArgumentCaptor.forClass(GraphRagEventSink.GraphRagEvent.class);
        verify(events).emit(captured.capture());
        assertEquals(GraphRagEventSink.Stage.RETRIEVE, captured.getValue().stage());
        assertEquals(GraphRagEventSink.Outcome.SUCCEEDED, captured.getValue().outcome());
        assertEquals(0, captured.getValue().outputCount());
        assertNotNull(captured.getValue().operationId());
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

    private static LightRagQueryResult queryResult(
            String authorizationFingerprint) {
        EvidenceReference evidence = new EvidenceReference(
                ORGANIZATION_ID,
                ASSET_ID,
                REVISION_ID,
                CHUNK_ID,
                ACL_ID,
                1L);
        return new LightRagQueryResult(
                LightRagQueryResult.Status.SUCCESS,
                "authorized context",
                "",
                new LightRagQueryResult.NoAnswer(),
                List.of(new LightRagQueryResult.Reference(
                        1,
                        evidence,
                        "Leave policy",
                        Map.of("sourceLabel", "Leave policy"))),
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
                        false,
                        false,
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

    private static final class NeverRecheckedStore
            extends SecureKnowledgeRetrievalStore {

        private int recheckCount;

        private NeverRecheckedStore() {
            super(null);
        }

        @Override
        List<SecureRetrievalCandidate> recheck(
                RetrievalScope scope,
                java.util.Collection<UUID> chunkIds) {
            recheckCount++;
            return List.of();
        }
    }
}
