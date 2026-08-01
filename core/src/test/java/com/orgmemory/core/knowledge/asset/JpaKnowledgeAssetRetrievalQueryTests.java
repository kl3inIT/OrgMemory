package com.orgmemory.core.knowledge.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JpaKnowledgeAssetRetrievalQueryTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();

    private final KnowledgeAssetRepository assets = mock(KnowledgeAssetRepository.class);
    private final KnowledgeAssetVersionRepository versions =
            mock(KnowledgeAssetVersionRepository.class);
    private final JpaKnowledgeAssetRetrievalQuery query =
            new JpaKnowledgeAssetRetrievalQuery(assets, versions);

    @Test
    void existenceAndAuthorizationScopesKeepOrganizationAndLifecycleOwnershipInAsset() {
        var scope = new KnowledgeAssetAuthorizationScope(ASSET_ID, SPACE_ID);
        when(assets.existsByIdAndOrganizationId(ASSET_ID, ORGANIZATION_ID))
                .thenReturn(true);
        when(assets.findActiveAuthorizationScopes(
                        ORGANIZATION_ID,
                        List.of(ASSET_ID)))
                .thenReturn(List.of(scope));

        assertTrue(query.exists(ORGANIZATION_ID, ASSET_ID));
        assertEquals(
                List.of(scope),
                query.findActiveAuthorizationScopes(
                        ORGANIZATION_ID,
                        List.of(ASSET_ID)));
    }

    @Test
    void catalogQueriesPreserveCurrentActiveVersionProjection() {
        KnowledgeCatalogItem item = item();
        when(versions.findCurrentCatalogItems(
                        ORGANIZATION_ID,
                        List.of(ASSET_ID)))
                .thenReturn(List.of(item));
        when(versions.findCurrentCatalogItem(
                        ORGANIZATION_ID,
                        ASSET_ID,
                        VERSION_ID))
                .thenReturn(Optional.of(item));
        when(versions.findCurrentCatalogItemByVersion(
                        ORGANIZATION_ID,
                        VERSION_ID,
                        List.of(ASSET_ID)))
                .thenReturn(Optional.of(item));

        assertEquals(
                List.of(item),
                query.findCurrentCatalogItems(
                        ORGANIZATION_ID,
                        List.of(ASSET_ID)));
        assertEquals(
                Optional.of(item),
                query.findCurrentCatalogItem(
                        ORGANIZATION_ID,
                        ASSET_ID,
                        VERSION_ID));
        assertEquals(
                Optional.of(item),
                query.findCurrentCatalogItemByVersion(
                        ORGANIZATION_ID,
                        VERSION_ID,
                        List.of(ASSET_ID)));
    }

    @Test
    void emptyAuthorizedSetsNeverReachPersistence() {
        assertEquals(
                List.of(),
                query.findActiveAuthorizationScopes(
                        ORGANIZATION_ID,
                        List.of()));
        assertEquals(
                List.of(),
                query.findCurrentCatalogItems(
                        ORGANIZATION_ID,
                        List.of()));
        assertFalse(query.findCurrentCatalogItemByVersion(
                        ORGANIZATION_ID,
                        VERSION_ID,
                        List.of())
                .isPresent());

        verifyNoInteractions(assets, versions);
    }

    @Test
    void exactExistenceUsesAssetIdBeforeOrganizationIdAtPersistenceBoundary() {
        query.exists(ORGANIZATION_ID, ASSET_ID);

        verify(assets).existsByIdAndOrganizationId(ASSET_ID, ORGANIZATION_ID);
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
}
