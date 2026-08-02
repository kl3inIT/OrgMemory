package com.orgmemory.core.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.graphrag.model.EvidenceReference;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CanonicalGraphEvidenceVerifierTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID SOURCE_OBJECT_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID ACL_ID = UUID.randomUUID();
    private static final UUID CHUNK_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-08-02T00:00:00Z");

    private final KnowledgeEvidenceScopeResolver evidenceScopes =
            mock(KnowledgeEvidenceScopeResolver.class);
    private final SecureKnowledgeRetrievalStore canonicalEvidence =
            mock(SecureKnowledgeRetrievalStore.class);
    private final CanonicalGraphEvidenceVerifier verifier =
            new CanonicalGraphEvidenceVerifier(evidenceScopes, canonicalEvidence);
    private final CurrentActor actor =
            new CurrentActor(USER_ID, ORGANIZATION_ID, null, "User", "user@example.com");

    @BeforeEach
    void setUpScope() {
        when(evidenceScopes.resolve(actor, "model-v1"))
                .thenReturn(resolvedScope());
    }

    @Test
    void exposesAnImmutableGraphSnapshotWithoutLeakingTheInternalScope() {
        VerifiedGraphEvidenceScope result =
                verifier.verifyScope(actor, "model-v1");

        assertEquals(ORGANIZATION_ID, result.organizationId());
        assertEquals(USER_ID, result.actorUserId());
        assertEquals("model-v1", result.authorizationModelId());
        assertEquals(EVALUATED_AT, result.evaluatedAt());
        assertEquals(Set.of(ASSET_ID), result.assetIdsByKnowledgeSpace().get(SPACE_ID));
        assertEquals(7L, result.authorizationGeneration(SPACE_ID));
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.assetIdsByKnowledgeSpace().put(UUID.randomUUID(), Set.of()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.assetIdsByKnowledgeSpace().get(SPACE_ID).add(UUID.randomUUID()));
    }

    @Test
    void translatesScopeResolutionFailureToTheStableRetrievalException() {
        var cause = new KnowledgeEvidenceScopeUnavailableException(
                "AUTHORIZED_OBJECT_SET_INVALID", "model-v1");
        when(evidenceScopes.resolve(actor, "model-v1")).thenThrow(cause);

        KnowledgeRetrievalUnavailableException thrown = assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> verifier.verifyScope(actor, "model-v1"));

        assertSame(cause, thrown.getCause());
    }

    @Test
    void acceptsOnlyTheExactCurrentCanonicalEvidenceIdentity() {
        VerifiedGraphEvidenceScope scope = verifier.verifyScope(actor, "model-v1");
        EvidenceReference evidence = evidence();
        when(canonicalEvidence.recheck(any(), any()))
                .thenReturn(List.of(candidate(
                        ORGANIZATION_ID,
                        CHUNK_ID,
                        ASSET_ID,
                        REVISION_ID,
                        ACL_ID)));

        assertTrue(verifier.isCurrentGoverningEvidence(scope, SPACE_ID, evidence));

        ArgumentCaptor<SecureKnowledgeRetrievalStore.RetrievalScope> retrievalScope =
                ArgumentCaptor.forClass(SecureKnowledgeRetrievalStore.RetrievalScope.class);
        verify(canonicalEvidence).recheck(retrievalScope.capture(), eq(List.of(CHUNK_ID)));
        assertEquals(ORGANIZATION_ID, retrievalScope.getValue().organizationId());
        assertEquals(List.of(ASSET_ID), retrievalScope.getValue().authorizedAssetIds());
        assertEquals("model-v1", retrievalScope.getValue().authorizationModelId());
    }

    @Test
    void rejectsEvidenceOutsideTheVerifiedSpaceWithoutTouchingTheStore() {
        VerifiedGraphEvidenceScope scope = verifier.verifyScope(actor, "model-v1");

        assertFalse(verifier.isCurrentGoverningEvidence(
                scope, UUID.randomUUID(), evidence()));
        assertFalse(verifier.isCurrentGoverningEvidence(
                scope,
                SPACE_ID,
                new EvidenceReference(
                        ORGANIZATION_ID,
                        UUID.randomUUID(),
                        REVISION_ID,
                        CHUNK_ID,
                        ACL_ID,
                        7)));

        verify(canonicalEvidence, never()).recheck(any(), any());
    }

    @Test
    void rejectsMissingDuplicateOrMismatchedCanonicalCandidates() {
        VerifiedGraphEvidenceScope scope = verifier.verifyScope(actor, "model-v1");
        EvidenceReference evidence = evidence();
        SecureRetrievalCandidate exact = candidate(
                ORGANIZATION_ID,
                CHUNK_ID,
                ASSET_ID,
                REVISION_ID,
                ACL_ID);
        when(canonicalEvidence.recheck(any(), any()))
                .thenReturn(
                        List.of(),
                        List.of(exact, exact),
                        List.of(candidate(
                                UUID.randomUUID(),
                                CHUNK_ID,
                                ASSET_ID,
                                REVISION_ID,
                                ACL_ID)),
                        List.of(candidate(
                                ORGANIZATION_ID,
                                UUID.randomUUID(),
                                ASSET_ID,
                                REVISION_ID,
                                ACL_ID)),
                        List.of(candidate(
                                ORGANIZATION_ID,
                                CHUNK_ID,
                                ASSET_ID,
                                UUID.randomUUID(),
                                ACL_ID)),
                        List.of(candidate(
                                ORGANIZATION_ID,
                                CHUNK_ID,
                                ASSET_ID,
                                REVISION_ID,
                                UUID.randomUUID())),
                        List.of(candidate(
                                ORGANIZATION_ID,
                                CHUNK_ID,
                                UUID.randomUUID(),
                                REVISION_ID,
                                ACL_ID)));

        assertFalse(verifier.isCurrentGoverningEvidence(scope, SPACE_ID, evidence));
        assertFalse(verifier.isCurrentGoverningEvidence(scope, SPACE_ID, evidence));
        assertFalse(verifier.isCurrentGoverningEvidence(scope, SPACE_ID, evidence));
        assertFalse(verifier.isCurrentGoverningEvidence(scope, SPACE_ID, evidence));
        assertFalse(verifier.isCurrentGoverningEvidence(scope, SPACE_ID, evidence));
        assertFalse(verifier.isCurrentGoverningEvidence(scope, SPACE_ID, evidence));
        assertFalse(verifier.isCurrentGoverningEvidence(scope, SPACE_ID, evidence));
    }

    @Test
    void comparesGraphSnapshotsUsingTheCallersExistingSemantics() {
        VerifiedGraphEvidenceScope initial = verifier.verifyScope(actor, "model-v1");
        VerifiedGraphEvidenceScope same = snapshot("model-v1", Set.of(ASSET_ID), 7L);
        VerifiedGraphEvidenceScope newGeneration = snapshot("model-v1", Set.of(ASSET_ID), 8L);
        VerifiedGraphEvidenceScope newModel = snapshot("model-v2", Set.of(ASSET_ID), 7L);

        assertTrue(initial.hasSameAuthorizationFingerprint(same, SPACE_ID));
        assertTrue(initial.hasSameAssetsAndGeneration(same, SPACE_ID));
        assertTrue(initial.hasSameSpaceScope(same, SPACE_ID));
        assertFalse(initial.hasSameAuthorizationFingerprint(newGeneration, SPACE_ID));
        assertFalse(initial.hasSameAssetsAndGeneration(newGeneration, SPACE_ID));
        assertFalse(initial.hasSameAuthorizationFingerprint(newModel, SPACE_ID));
        assertTrue(initial.hasSameAssetsAndGeneration(newModel, SPACE_ID));
        assertFalse(initial.hasSameSpaceScope(newModel, SPACE_ID));
        UUID unknownSpaceId = UUID.randomUUID();
        assertFalse(initial.hasSameSpaceScope(same, unknownSpaceId));
        assertThrows(
                IllegalArgumentException.class,
                () -> initial.forKnowledgeSpace(unknownSpaceId));
        assertThrows(
                IllegalArgumentException.class,
                () -> initial.authorizationGeneration(unknownSpaceId));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VerifiedGraphEvidenceScope(
                        ORGANIZATION_ID,
                        USER_ID,
                        null,
                        false,
                        "model-v1",
                        EVALUATED_AT,
                        Map.of(SPACE_ID, Set.of(ASSET_ID)),
                        Map.of()));
    }

    @Test
    void canonicalRecheckScopeContainsOnlyAssetsFromTheRequestedSpace() {
        UUID otherSpaceId = UUID.randomUUID();
        UUID otherAssetId = UUID.randomUUID();
        var scope = new VerifiedGraphEvidenceScope(
                ORGANIZATION_ID,
                USER_ID,
                null,
                false,
                "model-v1",
                EVALUATED_AT,
                Map.of(
                        SPACE_ID, Set.of(ASSET_ID),
                        otherSpaceId, Set.of(otherAssetId)),
                Map.of(
                        SPACE_ID, 7L,
                        otherSpaceId, 3L));

        assertEquals(
                List.of(ASSET_ID),
                scope.toRetrievalScope(SPACE_ID).authorizedAssetIds());
    }

    private static ResolvedKnowledgeEvidenceScope resolvedScope() {
        return new ResolvedKnowledgeEvidenceScope(
                ORGANIZATION_ID,
                USER_ID,
                null,
                false,
                "model-v1",
                EVALUATED_AT,
                Map.of(SPACE_ID, Set.of(ASSET_ID)),
                Map.of(SPACE_ID, 7L));
    }

    private static VerifiedGraphEvidenceScope snapshot(
            String authorizationModelId,
            Set<UUID> assets,
            long generation) {
        return new VerifiedGraphEvidenceScope(
                ORGANIZATION_ID,
                USER_ID,
                null,
                false,
                authorizationModelId,
                EVALUATED_AT,
                Map.of(SPACE_ID, assets),
                Map.of(SPACE_ID, generation));
    }

    private static EvidenceReference evidence() {
        return new EvidenceReference(
                ORGANIZATION_ID,
                ASSET_ID,
                REVISION_ID,
                CHUNK_ID,
                ACL_ID,
                7);
    }

    private static SecureRetrievalCandidate candidate(
            UUID organizationId,
            UUID chunkId,
            UUID assetId,
            UUID revisionId,
            UUID currentAclSnapshotId) {
        return new SecureRetrievalCandidate(
                organizationId,
                chunkId,
                assetId,
                SOURCE_OBJECT_ID,
                revisionId,
                "Policy",
                "Approved policy",
                "source://policy",
                null,
                null,
                null,
                0,
                currentAclSnapshotId,
                currentAclSnapshotId,
                "model-v1",
                PROFILE_ID,
                1);
    }
}
