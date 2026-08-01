package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.knowledge.retrieval.KnowledgeEvidenceScopeResolver;
import com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException;
import com.orgmemory.core.knowledge.retrieval.ResolvedKnowledgeEvidenceScope;
import com.orgmemory.core.knowledge.retrieval.SecureKnowledgeRetrievalStore;
import com.orgmemory.core.knowledge.retrieval.SecureRetrievalCandidate;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphQuery;

import com.orgmemory.core.knowledge.space.KnowledgeSpaceQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.curation.GraphCurationRecord;
import com.orgmemory.graphrag.curation.GraphCurationStore;
import com.orgmemory.graphrag.export.GraphExportReader;
import com.orgmemory.graphrag.model.EvidenceReference;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeGraphCurationServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID ACL_ID = UUID.randomUUID();
    private static final UUID CHUNK_ID = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();

    private final KnowledgeSpaceQuery spaces = mock(KnowledgeSpaceQuery.class);
    private final KnowledgeAssetGraphQuery assets =
            mock(KnowledgeAssetGraphQuery.class);
    private final RelationshipAuthorizationPort authorization =
            mock(RelationshipAuthorizationPort.class);
    private final KnowledgeEvidenceScopeResolver evidenceScopes =
            mock(KnowledgeEvidenceScopeResolver.class);
    private final SecureKnowledgeRetrievalStore canonicalEvidence =
            mock(SecureKnowledgeRetrievalStore.class);
    private final GraphExportReader graphs = mock(GraphExportReader.class);
    private final GraphCurationStore store = mock(GraphCurationStore.class);
    private final ModelInvocationCache modelCache =
            mock(ModelInvocationCache.class);
    private final RetrievalResultCache retrievalCache =
            mock(RetrievalResultCache.class);
    private final KnowledgeGraphCurationService service =
            new KnowledgeGraphCurationService(
                    spaces,
                    assets,
                    authorization,
                    evidenceScopes,
                    canonicalEvidence,
                    graphs,
                    store,
                    modelCache,
                    retrievalCache);
    private final CurrentActor actor =
            new CurrentActor(USER_ID, ORGANIZATION_ID, null, "User", "user@example.com");

    @BeforeEach
    void setUpSpaceAndEvidence() {
        when(spaces.isActive(ORGANIZATION_ID, SPACE_ID))
                .thenReturn(true);
        when(store.append(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(evidenceScopes.resolve(actor, "model-v1"))
                .thenReturn(new ResolvedKnowledgeEvidenceScope(
                        ORGANIZATION_ID,
                        USER_ID,
                        null,
                        false,
                        "model-v1",
                        Instant.parse("2026-07-24T00:00:00Z"),
                        Map.of(SPACE_ID, Set.of(ASSET_ID)),
                        Map.of(SPACE_ID, 7L)));
        when(canonicalEvidence.recheck(any(), any()))
                .thenReturn(List.of(new SecureRetrievalCandidate(
                        ORGANIZATION_ID,
                        CHUNK_ID,
                        ASSET_ID,
                        UUID.randomUUID(),
                        REVISION_ID,
                        "Policy",
                        "Approved policy",
                        "source://policy",
                        null,
                        null,
                        null,
                        0,
                        ACL_ID,
                        ACL_ID,
                        "model-v1",
                        UUID.randomUUID(),
                        1)));
    }

    @Test
    void curatorCreatesGovernedContributionAndInvalidatesNamespace() {
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.allow("model-v1"));
        var command = new KnowledgeGraphCurationCommand.CurateEntity(
                SPACE_ID,
                "curation-1",
                "correct entity description",
                7,
                ENTITY_ID,
                "Leave policy",
                "POLICY",
                "Approved policy",
                evidence());

        GraphCurationRecord result = service.apply(actor, command);

        var entity = (GraphCurationRecord.CuratedEntity) result;
        assertEquals(USER_ID, entity.provenance().actorUserId());
        assertEquals("model-v1", entity.provenance().authorizationModelId());
        assertEquals(evidence(), entity.governingEvidence());
        verify(modelCache).invalidate(entity.namespace());
        verify(retrievalCache).invalidateNamespace(entity.namespace());
    }

    @Test
    void unauthorizedCurationNeverReachesTheLedger() {
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.deny("DENIED", "model-v1"));

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> service.apply(
                        actor,
                        new KnowledgeGraphCurationCommand.CurateEntity(
                                SPACE_ID,
                                "curation-1",
                                "attempt",
                                7,
                                ENTITY_ID,
                                "Secret",
                                "POLICY",
                                "Denied",
                                evidence())));

        verify(store, never()).append(any(), any());
    }

    @Test
    void governingEvidenceCannotCrossKnowledgeSpaces() {
        when(authorization.check(any()))
                .thenReturn(AuthorizationDecision.allow("model-v1"));
        doThrow(new KnowledgeResourceNotFoundException())
                .when(assets)
                .requireInSpace(ORGANIZATION_ID, ASSET_ID, SPACE_ID);

        assertThrows(
                KnowledgeResourceNotFoundException.class,
                () -> service.apply(
                        actor,
                        new KnowledgeGraphCurationCommand.CurateEntity(
                                SPACE_ID,
                                "curation-1",
                                "attempt",
                                7,
                                ENTITY_ID,
                                "Secret",
                                "POLICY",
                                "Denied",
                                evidence())));
        verify(store, never()).append(any(), any());
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
}
