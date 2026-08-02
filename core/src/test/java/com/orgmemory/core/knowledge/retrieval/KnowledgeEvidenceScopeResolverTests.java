package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.knowledge.asset.KnowledgeAssetAuthorizationScope;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetRetrievalQuery;
import com.orgmemory.core.knowledge.acl.KnowledgeSpaceAclGenerationRef;
import com.orgmemory.core.knowledge.acl.SourceAclQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.BatchAuthorizationResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.KnowledgeAccessSubject;
import com.orgmemory.core.organization.KnowledgeAccessSubjectQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class KnowledgeEvidenceScopeResolverTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("71000000-0000-0000-0000-000000000002");
    private static final UUID SPACE_ID =
            UUID.fromString("71000000-0000-0000-0000-000000000003");
    private static final UUID ASSET_ID =
            UUID.fromString("71000000-0000-0000-0000-000000000004");
    private static final String MODEL_ID = "model-v1";

    @Test
    void assetInspectionRequiresRelationshipAuthorizationBeforeCanonicalVisibility() {
        KnowledgeAccessSubjectQuery subjects = mock(KnowledgeAccessSubjectQuery.class);
        RelationshipAuthorizationSetPort authorization =
                mock(RelationshipAuthorizationSetPort.class);
        SecureKnowledgeRetrievalStore canonical =
                mock(SecureKnowledgeRetrievalStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Clock> clocks = mock(ObjectProvider.class);
        CurrentActor actor = new CurrentActor(
                USER_ID,
                ORGANIZATION_ID,
                null,
                "User",
                "user@example.test");
        ResourceRef asset = ResourceRef.of(
                ORGANIZATION_ID,
                "knowledge_asset",
                ASSET_ID);
        when(subjects.findActive(ORGANIZATION_ID, USER_ID))
                .thenReturn(Optional.of(new KnowledgeAccessSubject(
                        USER_ID,
                        ORGANIZATION_ID,
                        null,
                        false)));
        when(authorization.batchCheck(any())).thenReturn(
                BatchAuthorizationResult.resolved(
                        java.util.Map.of(
                                asset,
                                AuthorizationDecision.deny(
                                        "RELATIONSHIP_DENIED",
                                        MODEL_ID)),
                        MODEL_ID));

        var resolver = new KnowledgeEvidenceScopeResolver(
                subjects,
                authorization,
                mock(KnowledgeAssetRetrievalQuery.class),
                mock(SourceAclQuery.class),
                canonical,
                new KnowledgeRetrievalProperties(null, null, null, null),
                clocks);

        KnowledgeAssetAccessInspector.AssetInspection result = resolver.inspectAsset(
                actor,
                ASSET_ID,
                MODEL_ID,
                Instant.parse("2026-08-02T00:00:00Z"));

        assertEquals(com.orgmemory.core.authorization.AccessState.DENIED, result.state());
        assertEquals("RELATIONSHIP_DENIED", result.reasonCode());
        verifyNoInteractions(canonical);
    }

    @Test
    void administratorUsesOpenFgaAssetVisibilityWithoutImplicitExecutiveAccess() {
        KnowledgeAccessSubjectQuery subjects = mock(KnowledgeAccessSubjectQuery.class);
        RelationshipAuthorizationSetPort authorization =
                mock(RelationshipAuthorizationSetPort.class);
        KnowledgeAssetRetrievalQuery assets = mock(KnowledgeAssetRetrievalQuery.class);
        SourceAclQuery aclQuery = mock(SourceAclQuery.class);
        SecureKnowledgeRetrievalStore canonical =
                mock(SecureKnowledgeRetrievalStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Clock> clocks = mock(ObjectProvider.class);

        when(subjects.findActive(ORGANIZATION_ID, USER_ID))
                .thenReturn(Optional.of(new KnowledgeAccessSubject(
                        USER_ID,
                        ORGANIZATION_ID,
                        null,
                        false)));
        when(authorization.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.resolved(
                        List.of(ResourceRef.of(
                                ORGANIZATION_ID,
                                "knowledge_asset",
                                ASSET_ID)),
                        MODEL_ID));
        when(assets.findActiveAuthorizationScopes(
                        ORGANIZATION_ID,
                        List.of(ASSET_ID)))
                .thenReturn(List.of(new KnowledgeAssetAuthorizationScope(
                        ASSET_ID,
                        SPACE_ID)));
        KnowledgeSpaceAclGenerationRef generation =
                new KnowledgeSpaceAclGenerationRef(SPACE_ID, 7L);
        when(aclQuery.maximumCurrentAclGenerations(
                        ORGANIZATION_ID,
                        List.of(ASSET_ID)))
                .thenReturn(List.of(generation));
        when(canonical.visibleKnowledgeAssetIds(any()))
                .thenReturn(List.of(ASSET_ID));
        when(clocks.getIfAvailable(any())).thenReturn(Clock.fixed(
                Instant.parse("2026-07-24T00:00:00Z"),
                ZoneOffset.UTC));

        var resolver = new KnowledgeEvidenceScopeResolver(
                subjects,
                authorization,
                assets,
                aclQuery,
                canonical,
                new KnowledgeRetrievalProperties(null, null, null, null),
                clocks);

        ResolvedKnowledgeEvidenceScope scope = resolver.resolve(
                new CurrentActor(
                        USER_ID,
                        ORGANIZATION_ID,
                        null,
                        "Admin",
                        "admin@example.test"),
                MODEL_ID);

        assertEquals(Set.of(ASSET_ID), scope.allAssetIds());
        assertEquals(7L, scope.aclGenerationByKnowledgeSpace().get(SPACE_ID));
        assertEquals(false, scope.actorExecutive());
    }
}
