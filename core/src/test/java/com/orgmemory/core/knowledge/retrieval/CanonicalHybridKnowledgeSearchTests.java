package com.orgmemory.core.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.BatchAuthorizationResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.retrieval.QueryEmbedding;
import com.orgmemory.core.knowledge.retrieval.QueryEmbeddingPort;
import com.orgmemory.core.knowledge.search.SecureKnowledgeSearchResult;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.permission.PermissionAuditCommand;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class CanonicalHybridKnowledgeSearchTests {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPARTMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SPACE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String MODEL_ID = "model-1";

    private final StubStore store = new StubStore();
    private final KnowledgeEvidenceScopeResolver evidenceScopes =
            mock(KnowledgeEvidenceScopeResolver.class);
    private final RelationshipAuthorizationPort entryAuthorization = mock(RelationshipAuthorizationPort.class);
    private final RelationshipAuthorizationSetPort authorization = mock(RelationshipAuthorizationSetPort.class);
    private final QueryEmbeddingPort embeddings = mock(QueryEmbeddingPort.class);
    private final PermissionAuditService audit = mock(PermissionAuditService.class);
    private CanonicalHybridKnowledgeSearch service;
    private CurrentActor actor;
    private UUID assetId;

    @BeforeEach
    void setUp() {
        actor = new CurrentActor(
                UUID.randomUUID(),
                ORGANIZATION_ID,
                DEPARTMENT_ID,
                "Laura",
                "laura@example.test");
        assetId = UUID.randomUUID();
        when(embeddings.embed(any(), any())).thenReturn(Optional.empty());
        when(entryAuthorization.check(any())).thenReturn(AuthorizationDecision.allow(MODEL_ID));
        when(evidenceScopes.resolve(actor, MODEL_ID)).thenReturn(
                new ResolvedKnowledgeEvidenceScope(
                        ORGANIZATION_ID,
                        actor.userId(),
                        DEPARTMENT_ID,
                        false,
                        MODEL_ID,
                        Instant.parse("2026-07-24T00:00:00Z"),
                        Map.of(SPACE_ID, Set.of(assetId)),
                        Map.of(SPACE_ID, 1L)));
        service = new CanonicalHybridKnowledgeSearch(
                store,
                evidenceScopes,
                new KnowledgeSearchAuthorizationService(
                        entryAuthorization,
                        audit),
                authorization,
                embeddings,
                audit,
                new KnowledgeRetrievalProperties(20, 5, 5_000, 1_000));
    }

    @Test
    void explicitEntryDenialStopsBeforeListingObjects() {
        when(entryAuthorization.check(any())).thenReturn(
                AuthorizationDecision.deny("RELATIONSHIP_DENIED", MODEL_ID));

        assertThrows(OrgMemoryAccessDeniedException.class,
                () -> service.search(actor, "leave policy", 10, "request-1"));

        verify(audit).record(any());
    }

    @Test
    void providerOutageFailsClosed() {
        when(evidenceScopes.resolve(actor, MODEL_ID)).thenThrow(
                new KnowledgeEvidenceScopeUnavailableException(
                        "OPENFGA_TIMEOUT",
                        MODEL_ID));

        assertThrows(KnowledgeRetrievalUnavailableException.class,
                () -> service.search(actor, "leave policy", 10, "request-2"));
    }

    @Test
    void emptyAuthorizedScopeReturnsAllowedEmptyWithoutBatchCheck() {
        when(evidenceScopes.resolve(actor, MODEL_ID)).thenReturn(scope(Set.of()));

        SecureKnowledgeSearchResult result =
                service.search(actor, "leave policy", 10, "request-empty");

        assertEquals(List.of(), result.evidence());
        verify(authorization, never()).batchCheck(any());
        ArgumentCaptor<PermissionAuditCommand> recorded =
                ArgumentCaptor.forClass(PermissionAuditCommand.class);
        verify(audit).record(recorded.capture());
        assertEquals(
                "NO_AUTHORIZED_KNOWLEDGE_ASSETS",
                recorded.getValue().reasonCode());
    }

    @Test
    void candidateOutsideListObjectsBoundaryFailsClosed() {
        store.lexical = List.of(candidate(UUID.randomUUID(), UUID.randomUUID()));

        assertThrows(KnowledgeRetrievalUnavailableException.class,
                () -> service.search(actor, "leave policy", 10, "request-3"));
    }

    @Test
    void crossTenantCandidateFailsClosedEvenWhenItsAssetIdWasListed() {
        store.lexical = List.of(candidate(UUID.randomUUID(), assetId, UUID.randomUUID()));

        assertThrows(KnowledgeRetrievalUnavailableException.class,
                () -> service.search(actor, "leave policy", 10, "request-cross-tenant"));
    }

    @Test
    void incompleteBatchCheckFailsClosed() {
        store.lexical = List.of(candidate(UUID.randomUUID(), assetId));
        when(authorization.batchCheck(any())).thenReturn(
                BatchAuthorizationResult.resolved(Map.of(), MODEL_ID));

        assertThrows(KnowledgeRetrievalUnavailableException.class,
                () -> service.search(actor, "leave policy", 10, "request-4"));
    }

    @Test
    void duplicateAssetResourcesAreCheckedOnlyOnce() {
        SecureRetrievalCandidate first = candidate(UUID.randomUUID(), assetId);
        SecureRetrievalCandidate second = candidate(UUID.randomUUID(), assetId);
        store.lexical = List.of(first, second);
        store.rechecked = List.of(first, second);
        ResourceRef resource = resource(assetId);
        when(authorization.batchCheck(any())).thenReturn(
                BatchAuthorizationResult.resolved(
                        Map.of(resource, AuthorizationDecision.allow(MODEL_ID)),
                        MODEL_ID));

        SecureKnowledgeSearchResult result =
                service.search(actor, "leave policy", 10, "request-duplicates");

        assertEquals(1, result.evidence().size());
        ArgumentCaptor<BatchAuthorizationQuery> query =
                ArgumentCaptor.forClass(BatchAuthorizationQuery.class);
        verify(authorization).batchCheck(query.capture());
        assertEquals(List.of(resource), query.getValue().resources());
    }

    @Test
    void mixedAllowAndDenyKeepsOnlyAllowedEvidence() {
        UUID deniedAssetId = UUID.randomUUID();
        SecureRetrievalCandidate allowedCandidate =
                candidate(UUID.randomUUID(), assetId);
        SecureRetrievalCandidate deniedCandidate =
                candidate(UUID.randomUUID(), deniedAssetId);
        when(evidenceScopes.resolve(actor, MODEL_ID))
                .thenReturn(scope(Set.of(assetId, deniedAssetId)));
        store.lexical = List.of(allowedCandidate, deniedCandidate);
        store.rechecked = List.of(allowedCandidate);
        when(authorization.batchCheck(any())).thenReturn(
                BatchAuthorizationResult.resolved(
                        Map.of(
                                resource(assetId),
                                AuthorizationDecision.allow(MODEL_ID),
                                resource(deniedAssetId),
                                AuthorizationDecision.deny(
                                        "RELATIONSHIP_DENIED",
                                        MODEL_ID)),
                        MODEL_ID));

        SecureKnowledgeSearchResult result =
                service.search(actor, "leave policy", 10, "request-mixed");

        assertEquals(1, result.evidence().size());
        assertEquals(
                allowedCandidate.chunkId(),
                result.evidence().getFirst().chunkId());
    }

    @Test
    void missingDecisionWithCompleteSizedBatchUsesIncompleteReason() {
        store.lexical = List.of(candidate(UUID.randomUUID(), assetId));
        when(authorization.batchCheck(any())).thenReturn(
                BatchAuthorizationResult.resolved(
                        Map.of(
                                resource(UUID.randomUUID()),
                                AuthorizationDecision.allow(MODEL_ID)),
                        MODEL_ID));

        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> service.search(
                        actor,
                        "leave policy",
                        10,
                        "request-missing-decision"));

        assertAuditReason("OPENFGA_BATCH_INCOMPLETE");
    }

    @Test
    void outerAndPerDecisionModelMismatchUseStableReason() {
        for (BatchAuthorizationResult result : List.of(
                BatchAuthorizationResult.resolved(
                        Map.of(resource(assetId),
                                AuthorizationDecision.allow("model-v2")),
                        "model-v2"),
                BatchAuthorizationResult.resolved(
                        Map.of(resource(assetId),
                                AuthorizationDecision.allow("model-v2")),
                        MODEL_ID))) {
            CanonicalHybridKnowledgeSearch fixtureService = freshService();
            store.lexical = List.of(candidate(UUID.randomUUID(), assetId));
            when(authorization.batchCheck(any())).thenReturn(result);

            assertThrows(
                    KnowledgeRetrievalUnavailableException.class,
                    () -> fixtureService.search(
                            actor,
                            "leave policy",
                            10,
                            "request-model-drift"));

            assertAuditReason("AUTHORIZATION_MODEL_MISMATCH");
            org.mockito.Mockito.clearInvocations(audit);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "OPENFGA_TIMEOUT",
            "OPENFGA_UNAVAILABLE",
            "OPENFGA_INTERRUPTED"
    })
    void batchProviderFailureReasonIsPreserved(String reasonCode) {
        store.lexical = List.of(candidate(UUID.randomUUID(), assetId));
        when(authorization.batchCheck(any())).thenReturn(
                BatchAuthorizationResult.indeterminate(reasonCode, MODEL_ID));

        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> service.search(
                        actor,
                        "leave policy",
                        10,
                        "request-provider-failure"));

        assertAuditReason(reasonCode);
    }

    @Test
    void authorizationModelMismatchFailsClosed() {
        when(evidenceScopes.resolve(actor, MODEL_ID)).thenThrow(
                new KnowledgeEvidenceScopeUnavailableException(
                        "AUTHORIZATION_MODEL_MISMATCH",
                        "different-model"));

        assertThrows(KnowledgeRetrievalUnavailableException.class,
                () -> service.search(actor, "leave policy", 10, "request-model-mismatch"));
    }

    @Test
    void citationMissingAtCanonicalRecheckIsOmitted() {
        SecureRetrievalCandidate candidate = candidate(UUID.randomUUID(), assetId);
        ResourceRef resource = ResourceRef.of(ORGANIZATION_ID, "knowledge_asset", assetId);
        store.lexical = List.of(candidate);
        store.rechecked = List.of();
        when(authorization.batchCheck(any())).thenReturn(BatchAuthorizationResult.resolved(
                Map.of(resource, AuthorizationDecision.allow(MODEL_ID)),
                MODEL_ID));

        SecureKnowledgeSearchResult result = service.search(actor, "leave policy", 10, "request-5");

        assertEquals(List.of(), result.evidence());
        verify(audit).recordAll(any());
    }

    private static SecureRetrievalCandidate candidate(UUID chunkId, UUID knowledgeAssetId) {
        return candidate(chunkId, knowledgeAssetId, ORGANIZATION_ID);
    }

    private ResolvedKnowledgeEvidenceScope scope(Set<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return new ResolvedKnowledgeEvidenceScope(
                    ORGANIZATION_ID,
                    actor.userId(),
                    DEPARTMENT_ID,
                    false,
                    MODEL_ID,
                    Instant.parse("2026-07-24T00:00:00Z"),
                    Map.of(),
                    Map.of());
        }
        return new ResolvedKnowledgeEvidenceScope(
                ORGANIZATION_ID,
                actor.userId(),
                DEPARTMENT_ID,
                false,
                MODEL_ID,
                Instant.parse("2026-07-24T00:00:00Z"),
                Map.of(SPACE_ID, assetIds),
                Map.of(SPACE_ID, 1L));
    }

    private ResourceRef resource(UUID knowledgeAssetId) {
        return ResourceRef.of(
                ORGANIZATION_ID,
                "knowledge_asset",
                knowledgeAssetId);
    }

    private CanonicalHybridKnowledgeSearch freshService() {
        return new CanonicalHybridKnowledgeSearch(
                store,
                evidenceScopes,
                new KnowledgeSearchAuthorizationService(
                        entryAuthorization,
                        audit),
                authorization,
                embeddings,
                audit,
                new KnowledgeRetrievalProperties(20, 5, 5_000, 1_000));
    }

    private void assertAuditReason(String reasonCode) {
        ArgumentCaptor<PermissionAuditCommand> recorded =
                ArgumentCaptor.forClass(PermissionAuditCommand.class);
        verify(audit).record(recorded.capture());
        assertEquals(reasonCode, recorded.getValue().reasonCode());
    }

    private static SecureRetrievalCandidate candidate(
            UUID chunkId,
            UUID knowledgeAssetId,
            UUID organizationId) {
        return new SecureRetrievalCandidate(
                organizationId,
                chunkId,
                knowledgeAssetId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Leave policy",
                "Employees receive annual leave.",
                "s3://orgmemory/source",
                1,
                1,
                "Annual leave",
                0.9,
                UUID.randomUUID(),
                UUID.randomUUID(),
                MODEL_ID,
                UUID.randomUUID(),
                1);
    }

    private static final class StubStore extends SecureKnowledgeRetrievalStore {
        private List<SecureRetrievalCandidate> lexical = List.of();
        private final List<SecureRetrievalCandidate> semantic = List.of();
        private List<SecureRetrievalCandidate> rechecked = List.of();

        private StubStore() {
            super(null);
        }

        @Override
        List<SecureRetrievalCandidate> lexical(RetrievalScope scope, String query, int candidateLimit) {
            return lexical;
        }

        @Override
        List<SecureRetrievalCandidate> semantic(
                RetrievalScope scope,
                QueryEmbedding embedding,
                int candidateLimit) {
            return semantic;
        }

        @Override
        public List<SecureRetrievalCandidate> recheck(
                RetrievalScope scope, Collection<UUID> chunkIds) {
            return rechecked;
        }
    }
}
