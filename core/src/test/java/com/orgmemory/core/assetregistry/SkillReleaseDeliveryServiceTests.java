package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.api.AssetIdentity;
import com.orgmemory.core.assetregistry.api.AssetIdentityQuery;
import com.orgmemory.core.assetregistry.api.AssetNotFoundException;
import com.orgmemory.core.assetregistry.api.AssetPortfolioState;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.assetregistry.consumption.AssetAvailability;
import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.assetregistry.consumption.AssetPublicationMode;
import com.orgmemory.core.assetregistry.skillstorage.SkillPackageStoragePort;
import com.orgmemory.core.organization.CurrentActor;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkillReleaseDeliveryServiceTests {

    @Test
    void searchesOnlyTheCanUseCatalogAndPinsExactSkillReleases() {
        Fixture fixture = fixture();
        AssetRecommendation recommendation = new AssetRecommendation(
                ASSET_ID,
                AssetType.SKILL,
                "support",
                "triage",
                "Support triage",
                "Triage customer issues",
                UUID.randomUUID(),
                AssetPortfolioState.ACTIVE,
                RELEASE_ID,
                "1.2.0",
                "c".repeat(64),
                AssetAvailability.AVAILABLE,
                java.time.Instant.parse("2026-07-27T10:00:00Z"));
        when(fixture.assets.catalog(
                        ACTOR,
                        "incident",
                        AssetType.SKILL,
                        AssetCatalogSort.RECENTLY_RELEASED,
                        1,
                        3))
                .thenReturn(new AssetRecommendationPage(
                        List.of(recommendation),
                        1,
                        1,
                        3,
                        1,
                        AssetCatalogSort.RECENTLY_RELEASED));

        var result = fixture.service.search(ACTOR, "incident", 3);

        assertEquals(1, result.size());
        assertEquals(RELEASE_ID, result.getFirst().releaseId());
        assertEquals("support", result.getFirst().namespace());
        verify(fixture.assets).catalog(
                ACTOR,
                "incident",
                AssetType.SKILL,
                AssetCatalogSort.RECENTLY_RELEASED,
                1,
                3);
    }

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("87000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("87000000-0000-0000-0000-000000000002");
    private static final UUID ASSET_ID =
            UUID.fromString("87000000-0000-0000-0000-000000000003");
    private static final UUID RELEASE_ID =
            UUID.fromString("87000000-0000-0000-0000-000000000004");
    private static final String PACKAGE_DIGEST = "a".repeat(64);
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID,
            ORGANIZATION_ID,
            null,
            "Skill user",
            "skill.user@example.test");

    @Test
    void rejectsAndClosesStoredBytesWhoseMetadataDoesNotMatchTheRelease() {
        Fixture fixture = fixture();
        TrackingInputStream stream = new TrackingInputStream();
        when(fixture.storage.open("private/skill.zip"))
                .thenReturn(new SkillPackageStoragePort.StoredSkillPackageContent(
                        stream,
                        new SkillPackageStoragePort.StoredSkillPackage(
                                "private/skill.zip",
                                7,
                                "application/zip",
                                "b".repeat(64))));

        assertThrows(
                AssetUnavailableException.class,
                () -> fixture.service.open(ACTOR, ASSET_ID, RELEASE_ID));

        assertTrue(stream.closed);
    }

    @Test
    void rejectsAReleaseWhosePackageReferenceIsMissing() {
        Fixture fixture = fixture();
        when(fixture.references.findByReleaseIdAndOrganizationId(
                        RELEASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                AssetUnavailableException.class,
                () -> fixture.service.describe(ACTOR, ASSET_ID, RELEASE_ID));

        verifyNoInteractions(fixture.storage);
    }

    @Test
    void rejectsAReleaseWhosePackageReferenceIsNotABlob() {
        Fixture fixture = fixture();
        when(fixture.reference.isBlobReference()).thenReturn(false);

        assertThrows(
                AssetUnavailableException.class,
                () -> fixture.service.describe(ACTOR, ASSET_ID, RELEASE_ID));

        verifyNoInteractions(fixture.storage);
    }

    @Test
    void resolvesCoordinateAndVersionBeforeApplyingTheLiveUseCheck() {
        Fixture fixture = fixture();
        AssetIdentity asset = assetIdentity();
        AssetRelease release = mock(AssetRelease.class);
        when(release.getId()).thenReturn(RELEASE_ID);
        when(fixture.identities.findByCoordinate(
                        ORGANIZATION_ID, "support", "triage"))
                .thenReturn(Optional.of(asset));
        when(fixture.releases.findByAssetIdAndOrganizationIdAndVersionLabel(
                        ASSET_ID, ORGANIZATION_ID, "1.2.0"))
                .thenReturn(Optional.of(release));

        var descriptor = fixture.service.describe(
                ACTOR, "Support", "Triage", "1.2.0");

        assertEquals(RELEASE_ID, descriptor.release().releaseId());
        verify(fixture.assets).releaseForUse(
                ACTOR, ASSET_ID, RELEASE_ID, AssetType.SKILL);
    }

    @Test
    void keepsInvalidVersionDetailsBehindTheOpaqueNotFoundError() {
        Fixture fixture = fixture();
        when(fixture.identities.findByCoordinate(
                        ORGANIZATION_ID, "support", "triage"))
                .thenReturn(Optional.of(assetIdentity()));

        AssetNotFoundException failure = assertThrows(
                AssetNotFoundException.class,
                () -> fixture.service.describe(
                        ACTOR, "support", "triage", "not a version"));

        assertTrue(failure.getCause() instanceof IllegalArgumentException);
    }

    private static Fixture fixture() {
        AssetRegistryService assets = mock(AssetRegistryService.class);
        AssetIdentityQuery identities = mock(AssetIdentityQuery.class);
        AssetReleaseRepository releases = mock(AssetReleaseRepository.class);
        AssetPayloadReferenceRepository references =
                mock(AssetPayloadReferenceRepository.class);
        SkillPackageStoragePort storage = mock(SkillPackageStoragePort.class);
        AssetPayloadReference reference = mock(AssetPayloadReference.class);
        when(assets.releaseForUse(ACTOR, ASSET_ID, RELEASE_ID, AssetType.SKILL))
                .thenReturn(release());
        when(references.findByReleaseIdAndOrganizationId(
                        RELEASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(reference));
        when(reference.isBlobReference()).thenReturn(true);
        when(reference.getReferenceValue()).thenReturn("private/skill.zip");
        when(reference.getDigest()).thenReturn(PACKAGE_DIGEST);
        when(reference.getContentLength()).thenReturn(7L);
        when(reference.getMediaType()).thenReturn("application/zip");
        return new Fixture(
                new SkillReleaseDeliveryService(
                        assets, identities, releases, references, storage),
                assets,
                identities,
                releases,
                references,
                reference,
                storage);
    }

    private static AssetConsumptionRelease release() {
        return new AssetConsumptionRelease(
                ASSET_ID,
                RELEASE_ID,
                UUID.randomUUID(),
                AssetType.SKILL,
                "support",
                "triage",
                "1.2.0",
                AssetPublicationMode.DIRECT,
                "Support triage",
                "Triage customer issues",
                "INTERNAL",
                "1",
                "{\"profile\":\"skill\"}",
                "c".repeat(64),
                AssetAvailability.AVAILABLE,
                java.time.Instant.parse("2026-07-27T10:00:00Z"));
    }

    private static AssetIdentity assetIdentity() {
        return new AssetIdentity(
                ORGANIZATION_ID,
                ASSET_ID,
                AssetType.SKILL,
                "support",
                "triage",
                UUID.randomUUID(),
                AssetPortfolioState.DRAFT_ONLY,
                true);
    }

    private record Fixture(
            SkillReleaseDeliveryService service,
            AssetRegistryService assets,
            AssetIdentityQuery identities,
            AssetReleaseRepository releases,
            AssetPayloadReferenceRepository references,
            AssetPayloadReference reference,
            SkillPackageStoragePort storage) {
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        private TrackingInputStream() {
            super(new byte[7]);
        }

        @Override
        public void close() throws java.io.IOException {
            closed = true;
            super.close();
        }
    }
}
