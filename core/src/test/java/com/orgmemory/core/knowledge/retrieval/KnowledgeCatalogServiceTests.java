package com.orgmemory.core.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.asset.KnowledgeAssetRetrievalQuery;
import com.orgmemory.core.knowledge.asset.KnowledgeCatalogItem;
import com.orgmemory.core.knowledge.catalog.KnowledgeCatalogEntry;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeCatalogServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID, ORGANIZATION_ID, null, "User", "user@example.test");

    private final KnowledgeEvidenceScopeResolver scopes =
            mock(KnowledgeEvidenceScopeResolver.class);
    private final KnowledgeAssetRetrievalQuery assets =
            mock(KnowledgeAssetRetrievalQuery.class);
    private final KnowledgeCatalogService catalog =
            new KnowledgeCatalogService(scopes, assets);

    @Test
    void catalogUsesTheCanonicalPermissionResolvedAssetSet() {
        KnowledgeCatalogItem item = item();
        when(scopes.resolve(ACTOR, null)).thenReturn(scope(Set.of(ASSET_ID)));
        when(assets.findCurrentCatalogItems(
                        ORGANIZATION_ID, Set.of(ASSET_ID)))
                .thenReturn(List.of(item));

        assertEquals(List.of(entry()), catalog.list(ACTOR));
    }

    @Test
    void deniedExactVersionsReturnNoMetadataAndNeverReachTheRepository() {
        when(scopes.resolve(ACTOR, null)).thenReturn(scope(Set.of()));

        assertTrue(catalog.findExactVisible(
                        ACTOR, ASSET_ID, VERSION_ID)
                .isEmpty());
        verifyNoInteractions(assets);
    }

    @Test
    void deniedVersionOnlyLookupsResolveAuthorizationBeforeTouchingPersistence() {
        when(scopes.resolve(ACTOR, null)).thenReturn(scope(Set.of()));

        assertTrue(catalog.findVersionVisible(ACTOR, VERSION_ID).isEmpty());

        verifyNoInteractions(assets);
    }

    @Test
    void onlyTheExactCurrentVersionCanBeFederated() {
        KnowledgeCatalogItem item = item();
        when(scopes.resolve(ACTOR, null)).thenReturn(scope(Set.of(ASSET_ID)));
        when(assets.findCurrentCatalogItem(
                        ORGANIZATION_ID, ASSET_ID, VERSION_ID))
                .thenReturn(Optional.of(item));

        assertEquals(
                Optional.of(entry()),
                catalog.findExactVisible(
                        ACTOR, ASSET_ID, VERSION_ID));
    }

    @Test
    void versionOnlyLookupUsesTheAuthorizedAssetSetAndMapsEveryField() {
        KnowledgeCatalogItem item = item();
        when(scopes.resolve(ACTOR, null)).thenReturn(scope(Set.of(ASSET_ID)));
        when(assets.findCurrentCatalogItemByVersion(
                        ORGANIZATION_ID, VERSION_ID, Set.of(ASSET_ID)))
                .thenReturn(Optional.of(item));

        assertEquals(
                Optional.of(entry()),
                catalog.findVersionVisible(ACTOR, VERSION_ID));
    }

    @Test
    void authorizedMissingVersionAndDeniedVersionBothReturnOpaqueAbsence() {
        when(scopes.resolve(ACTOR, null)).thenReturn(scope(Set.of(ASSET_ID)));
        when(assets.findCurrentCatalogItemByVersion(
                        ORGANIZATION_ID, VERSION_ID, Set.of(ASSET_ID)))
                .thenReturn(Optional.empty());

        assertEquals(Optional.empty(), catalog.findVersionVisible(ACTOR, VERSION_ID));
    }

    @Test
    void authorizationIndeterminacyPropagatesWithoutTouchingPersistence() {
        IllegalStateException failure = new IllegalStateException("indeterminate");
        when(scopes.resolve(ACTOR, null)).thenThrow(failure);

        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () -> catalog.findVersionVisible(ACTOR, VERSION_ID)));
        verifyNoInteractions(assets);
    }

    private static ResolvedKnowledgeEvidenceScope scope(Set<UUID> assetIds) {
        Map<UUID, Set<UUID>> bySpace = assetIds.isEmpty()
                ? Map.of()
                : Map.of(SPACE_ID, assetIds);
        Map<UUID, Long> generations = assetIds.isEmpty()
                ? Map.of()
                : Map.of(SPACE_ID, 1L);
        return new ResolvedKnowledgeEvidenceScope(
                ORGANIZATION_ID,
                USER_ID,
                null,
                false,
                "model-1",
                Instant.now(),
                bySpace,
                generations);
    }

    private static KnowledgeCatalogItem item() {
        return new KnowledgeCatalogItem(
                ASSET_ID,
                VERSION_ID,
                3,
                SPACE_ID,
                "Support policy",
                "en",
                KnowledgeClassification.INTERNAL,
                "a".repeat(64));
    }

    private static KnowledgeCatalogEntry entry() {
        return new KnowledgeCatalogEntry(
                ASSET_ID,
                VERSION_ID,
                3,
                SPACE_ID,
                "Support policy",
                "en",
                KnowledgeClassification.INTERNAL,
                "a".repeat(64));
    }
}
