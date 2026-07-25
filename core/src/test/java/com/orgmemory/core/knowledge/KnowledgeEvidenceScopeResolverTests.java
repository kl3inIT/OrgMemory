package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.UserRole;
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
    void administratorUsesOpenFgaAssetVisibilityWithoutImplicitExecutiveAccess() {
        AppUserRepository users = mock(AppUserRepository.class);
        RelationshipAuthorizationSetPort authorization =
                mock(RelationshipAuthorizationSetPort.class);
        KnowledgeAssetRepository assets = mock(KnowledgeAssetRepository.class);
        SourceAclSnapshotRepository snapshots =
                mock(SourceAclSnapshotRepository.class);
        SecureKnowledgeRetrievalStore canonical =
                mock(SecureKnowledgeRetrievalStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Clock> clocks = mock(ObjectProvider.class);

        AppUser administrator = new AppUser(
                ORGANIZATION_ID,
                null,
                "Admin",
                "admin@example.test",
                UserRole.ADMIN);
        when(users.findById(USER_ID)).thenReturn(Optional.of(administrator));
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
        when(snapshots.maximumCurrentAclGeneration(
                        ORGANIZATION_ID,
                        Set.of(ASSET_ID)))
                .thenReturn(7L);
        when(canonical.visibleKnowledgeAssetIds(any()))
                .thenReturn(List.of(ASSET_ID));
        when(clocks.getIfAvailable(any())).thenReturn(Clock.fixed(
                Instant.parse("2026-07-24T00:00:00Z"),
                ZoneOffset.UTC));

        var resolver = new KnowledgeEvidenceScopeResolver(
                users,
                authorization,
                assets,
                snapshots,
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
