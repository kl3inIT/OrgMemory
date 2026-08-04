package com.orgmemory.core.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.BatchAuthorizationResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CanonicalEvidenceAuthorizationServiceTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000002");
    private static final UUID SPACE_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000003");
    private static final UUID ASSET_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000004");
    private static final UUID OBJECT_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000005");
    private static final UUID REVISION_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000006");
    private static final UUID CHUNK_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000007");
    private static final UUID ACL_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000008");
    private static final UUID PROFILE_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000010");
    private static final UUID SECOND_ASSET_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000011");
    private static final UUID SECOND_CHUNK_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000012");
    private static final String MODEL_ID = "model-v1";
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID,
            ORGANIZATION_ID,
            null,
            "User",
            "user@example.test");

    @Test
    void verifiesCurrentCanonicalEvidenceOncePerCitationRequest() {
        Fixture fixture = new Fixture();
        fixture.allow();
        SecureRetrievalCandidate candidate = candidate(ACL_ID);
        when(fixture.canonical.recheck(any(), any()))
                .thenReturn(List.of(candidate));

        var verified = fixture.service.verify(
                ACTOR,
                "request-1",
                "citation:request-1",
                List.of(CHUNK_ID));

        assertEquals(List.of(candidate), verified.candidates());
        assertEquals(MODEL_ID, verified.authorizationModelId());
    }

    @Test
    void rejectsEmptyAndDuplicateChunkInputsBeforeAuthorization() {
        Fixture fixture = new Fixture();

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.verify(
                        ACTOR,
                        "request-empty",
                        "citation:request-empty",
                        List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.verify(
                        ACTOR,
                        "request-duplicate",
                        "citation:request-duplicate",
                        List.of(CHUNK_ID, CHUNK_ID)));
    }

    @Test
    void duplicateAssetResourcesAreCheckedOnlyOnce() {
        Fixture fixture = new Fixture();
        fixture.allow();
        when(fixture.canonical.recheck(any(), any()))
                .thenReturn(List.of(
                        candidate(CHUNK_ID, ASSET_ID),
                        candidate(SECOND_CHUNK_ID, ASSET_ID)));

        fixture.service.verify(
                ACTOR,
                "request-duplicates",
                "citation:request-duplicates",
                List.of(CHUNK_ID, SECOND_CHUNK_ID));

        ArgumentCaptor<BatchAuthorizationQuery> query =
                ArgumentCaptor.forClass(BatchAuthorizationQuery.class);
        verify(fixture.authorization).batchCheck(query.capture());
        assertEquals(1, query.getValue().resources().size());
    }

    @Test
    void missingCanonicalEvidenceFailsClosed() {
        Fixture fixture = new Fixture();
        fixture.allow();
        when(fixture.canonical.recheck(any(), any()))
                .thenReturn(List.of());

        CanonicalEvidenceAuthorizationException denied = assertThrows(
                CanonicalEvidenceAuthorizationException.class,
                () -> fixture.service.verify(
                        ACTOR,
                        "request-1",
                        "citation:request-1",
                        List.of(CHUNK_ID)));

        assertEquals(
                "CITATION_NOT_VISIBLE",
                denied.reasonCode());
    }

    @Test
    void finalOpenFgaDenialFailsClosed() {
        Fixture fixture = new Fixture();
        when(fixture.search.require(any(), any(), any()))
                .thenReturn(MODEL_ID);
        when(fixture.scopes.resolve(ACTOR, MODEL_ID))
                .thenReturn(scope());
        when(fixture.canonical.recheck(any(), any()))
                .thenReturn(List.of(candidate(ACL_ID)));
        ResourceRef resource = ResourceRef.of(
                ORGANIZATION_ID,
                "knowledge_asset",
                ASSET_ID);
        when(fixture.authorization.batchCheck(any()))
                .thenReturn(BatchAuthorizationResult.resolved(
                        Map.of(
                                resource,
                                AuthorizationDecision.deny(
                                        "RELATIONSHIP_DENIED",
                                        MODEL_ID)),
                        MODEL_ID));

        CanonicalEvidenceAuthorizationException denied = assertThrows(
                CanonicalEvidenceAuthorizationException.class,
                () -> fixture.service.verify(
                        ACTOR,
                        "request-1",
                        "citation:request-1",
                        List.of(CHUNK_ID)));

        assertEquals(
                "CITATION_OPENFGA_RECHECK_DENIED",
                denied.reasonCode());
    }

    @Test
    void oneDeniedAssetRejectsTheEntireCitationSet() {
        Fixture fixture = new Fixture();
        when(fixture.search.require(any(), any(), any()))
                .thenReturn(MODEL_ID);
        when(fixture.scopes.resolve(ACTOR, MODEL_ID))
                .thenReturn(scope(Set.of(ASSET_ID, SECOND_ASSET_ID)));
        when(fixture.canonical.recheck(any(), any()))
                .thenReturn(List.of(
                        candidate(CHUNK_ID, ASSET_ID),
                        candidate(SECOND_CHUNK_ID, SECOND_ASSET_ID)));
        ResourceRef allowed = resource(ASSET_ID);
        ResourceRef denied = resource(SECOND_ASSET_ID);
        when(fixture.authorization.batchCheck(any()))
                .thenReturn(BatchAuthorizationResult.resolved(
                        Map.of(
                                allowed, AuthorizationDecision.allow(MODEL_ID),
                                denied, AuthorizationDecision.deny(
                                        "RELATIONSHIP_DENIED",
                                        MODEL_ID)),
                        MODEL_ID));

        CanonicalEvidenceAuthorizationException exception = assertThrows(
                CanonicalEvidenceAuthorizationException.class,
                () -> fixture.service.verify(
                        ACTOR,
                        "request-mixed",
                        "citation:request-mixed",
                        List.of(CHUNK_ID, SECOND_CHUNK_ID)));

        assertEquals(
                "CITATION_OPENFGA_RECHECK_DENIED",
                exception.reasonCode());
    }

    @Test
    void missingAndPerDecisionModelMismatchCollapseToCitationDenial() {
        for (BatchAuthorizationResult result : List.of(
                BatchAuthorizationResult.resolved(
                        Map.of(resource(SECOND_ASSET_ID),
                                AuthorizationDecision.allow(MODEL_ID)),
                        MODEL_ID),
                BatchAuthorizationResult.resolved(
                        Map.of(resource(ASSET_ID),
                                AuthorizationDecision.allow("model-v2")),
                        MODEL_ID))) {
            Fixture fixture = new Fixture();
            fixture.canonicalCandidate();
            when(fixture.authorization.batchCheck(any())).thenReturn(result);

            CanonicalEvidenceAuthorizationException exception = assertThrows(
                    CanonicalEvidenceAuthorizationException.class,
                    () -> fixture.service.verify(
                            ACTOR,
                            "request-denied",
                            "citation:request-denied",
                            List.of(CHUNK_ID)));

            assertEquals(
                    "CITATION_OPENFGA_RECHECK_DENIED",
                    exception.reasonCode());
        }
    }

    @Test
    void providerAndOuterModelFailuresKeepTheirAuditReasons() {
        for (BatchAuthorizationResult result : List.of(
                BatchAuthorizationResult.indeterminate(
                        "OPENFGA_TIMEOUT",
                        MODEL_ID),
                BatchAuthorizationResult.indeterminate(
                        "OPENFGA_UNAVAILABLE",
                        MODEL_ID),
                BatchAuthorizationResult.indeterminate(
                        "OPENFGA_INTERRUPTED",
                        MODEL_ID),
                BatchAuthorizationResult.resolved(
                        Map.of(resource(ASSET_ID),
                                AuthorizationDecision.allow("model-v2")),
                        "model-v2"))) {
            Fixture fixture = new Fixture();
            fixture.canonicalCandidate();
            when(fixture.authorization.batchCheck(any())).thenReturn(result);

            assertThrows(
                    KnowledgeRetrievalUnavailableException.class,
                    () -> fixture.service.verify(
                            ACTOR,
                            "request-unavailable",
                            "citation:request-unavailable",
                            List.of(CHUNK_ID)));

            ArgumentCaptor<PermissionAuditCommand> audit =
                    ArgumentCaptor.forClass(PermissionAuditCommand.class);
            verify(fixture.audit).record(audit.capture());
            assertEquals(result.reasonCode(), audit.getValue().reasonCode());
        }
    }

    @Test
    void replayHydrationFiltersDeniedEvidenceWithoutSuppressingAllowedEvidence() {
        Fixture fixture = new Fixture();
        when(fixture.scopes.resolve(ACTOR, MODEL_ID))
                .thenReturn(scope(Set.of(ASSET_ID, SECOND_ASSET_ID)));
        when(fixture.canonical.recheck(any(), any()))
                .thenReturn(List.of(
                        candidate(CHUNK_ID, ASSET_ID),
                        candidate(SECOND_CHUNK_ID, SECOND_ASSET_ID)));
        when(fixture.authorization.batchCheck(any()))
                .thenReturn(BatchAuthorizationResult.resolved(
                        Map.of(
                                resource(ASSET_ID), AuthorizationDecision.allow(MODEL_ID),
                                resource(SECOND_ASSET_ID), AuthorizationDecision.deny(
                                        "RELATIONSHIP_DENIED", MODEL_ID)),
                        MODEL_ID));

        var hydrated = fixture.service.filterVisible(
                ACTOR,
                "request-replay",
                "citation-hydration",
                List.of(CHUNK_ID, SECOND_CHUNK_ID));

        assertEquals(List.of(CHUNK_ID), hydrated.candidates().stream()
                .map(SecureRetrievalCandidate::chunkId)
                .toList());
    }

    @Test
    void replayHydrationUsesFixedAuthorizationBatchesAndFailsOnIndeterminateResults() {
        Fixture fixture = new Fixture();
        List<UUID> assets = java.util.stream.IntStream.range(0, 21)
                .mapToObj(ignored -> UUID.randomUUID())
                .toList();
        List<UUID> chunks = java.util.stream.IntStream.range(0, 21)
                .mapToObj(ignored -> UUID.randomUUID())
                .toList();
        when(fixture.scopes.resolve(ACTOR, MODEL_ID))
                .thenReturn(scope(Set.copyOf(assets)));
        when(fixture.canonical.recheck(any(), any()))
                .thenReturn(java.util.stream.IntStream.range(0, 21)
                        .mapToObj(index -> candidate(chunks.get(index), assets.get(index)))
                        .toList());
        when(fixture.authorization.batchCheck(any()))
                .thenAnswer(invocation -> {
                    BatchAuthorizationQuery query = invocation.getArgument(0);
                    return BatchAuthorizationResult.resolved(
                            query.resources().stream().collect(java.util.stream.Collectors.toMap(
                                    resource -> resource,
                                    ignored -> AuthorizationDecision.allow(MODEL_ID))),
                            MODEL_ID);
                });

        assertEquals(21, fixture.service.filterVisible(
                        ACTOR, "request-batches", "citation-hydration", chunks)
                .candidates().size());
        verify(fixture.authorization, times(2)).batchCheck(any());

        doReturn(BatchAuthorizationResult.indeterminate(
                        "OPENFGA_TIMEOUT", MODEL_ID))
                .when(fixture.authorization)
                .batchCheck(any());
        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> fixture.service.filterVisible(
                        ACTOR, "request-timeout", "citation-hydration", chunks));
    }

    private static ResolvedKnowledgeEvidenceScope scope() {
        return scope(Set.of(ASSET_ID));
    }

    private static ResolvedKnowledgeEvidenceScope scope(Set<UUID> assetIds) {
        return new ResolvedKnowledgeEvidenceScope(
                ORGANIZATION_ID,
                USER_ID,
                null,
                false,
                MODEL_ID,
                Instant.parse("2026-07-24T00:00:00Z"),
                Map.of(SPACE_ID, assetIds),
                Map.of(SPACE_ID, 1L));
    }

    private static SecureRetrievalCandidate candidate(UUID aclId) {
        return candidate(CHUNK_ID, ASSET_ID, aclId);
    }

    private static SecureRetrievalCandidate candidate(
            UUID chunkId,
            UUID assetId) {
        return candidate(chunkId, assetId, ACL_ID);
    }

    private static SecureRetrievalCandidate candidate(
            UUID chunkId,
            UUID assetId,
            UUID aclId) {
        return new SecureRetrievalCandidate(
                ORGANIZATION_ID,
                chunkId,
                assetId,
                OBJECT_ID,
                REVISION_ID,
                "Policy",
                "Approved evidence",
                null,
                null,
                null,
                null,
                0.0,
                ACL_ID,
                aclId,
                MODEL_ID,
                PROFILE_ID,
                1L);
    }

    private static ResourceRef resource(UUID assetId) {
        return ResourceRef.of(
                ORGANIZATION_ID,
                "knowledge_asset",
                assetId);
    }

    private static final class Fixture {

        private final KnowledgeSearchAuthorizationService search =
                mock(KnowledgeSearchAuthorizationService.class);
        private final KnowledgeEvidenceScopeResolver scopes =
                mock(KnowledgeEvidenceScopeResolver.class);
        private final RelationshipAuthorizationSetPort authorization =
                mock(RelationshipAuthorizationSetPort.class);
        private final PermissionAuditService audit =
                mock(PermissionAuditService.class);
        private final SecureKnowledgeRetrievalStore canonical =
                mock(SecureKnowledgeRetrievalStore.class);
        private final CanonicalEvidenceAuthorizationService service =
                new CanonicalEvidenceAuthorizationService(
                        new KnowledgeSearchAuthorizationService(
                                query -> AuthorizationDecision.allow(MODEL_ID),
                                audit),
                        scopes,
                        authorization,
                        canonical);

        private void allow() {
            when(scopes.resolve(ACTOR, MODEL_ID))
                    .thenReturn(scope());
            when(authorization.batchCheck(any()))
                    .thenReturn(BatchAuthorizationResult.resolved(
                            Map.of(
                                    resource(ASSET_ID),
                                    AuthorizationDecision.allow(
                                            MODEL_ID)),
                            MODEL_ID));
        }

        private void canonicalCandidate() {
            when(scopes.resolve(ACTOR, MODEL_ID))
                    .thenReturn(scope());
            when(canonical.recheck(any(), any()))
                    .thenReturn(List.of(candidate(ACL_ID)));
        }
    }
}
