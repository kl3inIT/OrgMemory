package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.BatchAuthorizationResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.organization.UserRole;
import com.orgmemory.core.permission.PermissionAuditService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecureKnowledgeRetrievalServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPARTMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String MODEL_ID = "model-1";

    private final StubStore store = new StubStore();
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final RelationshipAuthorizationPort entryAuthorization = mock(RelationshipAuthorizationPort.class);
    private final RelationshipAuthorizationSetPort authorization = mock(RelationshipAuthorizationSetPort.class);
    private final QueryEmbeddingPort embeddings = mock(QueryEmbeddingPort.class);
    private final PermissionAuditService audit = mock(PermissionAuditService.class);
    private SecureKnowledgeRetrievalService service;
    private CurrentActor actor;
    private UUID assetId;

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser(
                ORGANIZATION_ID,
                DEPARTMENT_ID,
                "Laura",
                "laura@example.test",
                UserRole.EMPLOYEE);
        actor = new CurrentActor(
                user.getId(), ORGANIZATION_ID, DEPARTMENT_ID, user.getName(), user.getEmail());
        assetId = UUID.randomUUID();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(embeddings.embed(any(), any())).thenReturn(Optional.empty());
        when(entryAuthorization.check(any())).thenReturn(AuthorizationDecision.allow(MODEL_ID));
        when(authorization.listAuthorizedResources(any())).thenReturn(AuthorizedResourceSetResult.resolved(
                List.of(ResourceRef.of(ORGANIZATION_ID, "knowledge_asset", assetId)),
                MODEL_ID));
        service = new SecureKnowledgeRetrievalService(
                store,
                users,
                entryAuthorization,
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
        when(authorization.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.indeterminate("OPENFGA_TIMEOUT", MODEL_ID));

        assertThrows(KnowledgeRetrievalUnavailableException.class,
                () -> service.search(actor, "leave policy", 10, "request-2"));
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
    void authorizationModelMismatchFailsClosed() {
        when(authorization.listAuthorizedResources(any())).thenReturn(AuthorizedResourceSetResult.resolved(
                List.of(ResourceRef.of(ORGANIZATION_ID, "knowledge_asset", assetId)),
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
        List<SecureRetrievalCandidate> recheck(RetrievalScope scope, Collection<UUID> chunkIds) {
            return rechecked;
        }
    }
}
