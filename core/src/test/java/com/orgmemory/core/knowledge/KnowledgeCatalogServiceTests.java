package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    private final KnowledgeAssetVersionRepository versions =
            mock(KnowledgeAssetVersionRepository.class);
    private final KnowledgeCatalogService catalog =
            new KnowledgeCatalogService(scopes, versions);

    @Test
    void catalogUsesTheCanonicalPermissionResolvedAssetSet() {
        KnowledgeCatalogItem item = item();
        when(scopes.resolve(ACTOR, null)).thenReturn(scope(Set.of(ASSET_ID)));
        when(versions.findCurrentCatalogItems(
                        ORGANIZATION_ID, Set.of(ASSET_ID)))
                .thenReturn(List.of(item));

        assertEquals(List.of(item), catalog.list(ACTOR));
    }

    @Test
    void deniedExactVersionsReturnNoMetadataAndNeverReachTheRepository() {
        when(scopes.resolve(ACTOR, null)).thenReturn(scope(Set.of()));

        assertTrue(catalog.findExactVisible(
                        ACTOR, ASSET_ID, VERSION_ID)
                .isEmpty());
        verifyNoInteractions(versions);
    }

    @Test
    void onlyTheExactCurrentVersionCanBeFederated() {
        KnowledgeCatalogItem item = item();
        when(scopes.resolve(ACTOR, null)).thenReturn(scope(Set.of(ASSET_ID)));
        when(versions.findCurrentCatalogItem(
                        ORGANIZATION_ID, ASSET_ID, VERSION_ID))
                .thenReturn(Optional.of(item));

        assertEquals(
                Optional.of(item),
                catalog.findExactVisible(
                        ACTOR, ASSET_ID, VERSION_ID));
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
}
