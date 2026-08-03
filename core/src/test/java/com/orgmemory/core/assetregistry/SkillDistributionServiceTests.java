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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkillDistributionServiceTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("85000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("85000000-0000-0000-0000-000000000002");
    private static final UUID ASSET_ID =
            UUID.fromString("85000000-0000-0000-0000-000000000003");
    private static final UUID RELEASE_ID =
            UUID.fromString("85000000-0000-0000-0000-000000000004");
    private static final String PACKAGE_DIGEST = "a".repeat(64);
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID,
            ORGANIZATION_ID,
            null,
            "Skill user",
            "skill.user@example.test");

    @Test
    void returnsAnExactManifestWithoutExposingTheStorageReference() {
        Fixture fixture = fixture();

        SkillInstallManifest manifest =
                fixture.service.manifest(ACTOR, ASSET_ID, RELEASE_ID);

        assertEquals("support/triage", manifest.coordinate());
        assertEquals("1.2.0", manifest.version());
        assertEquals(AssetPublicationMode.DIRECT, manifest.publicationMode());
        assertEquals(PACKAGE_DIGEST, manifest.packageDigest());
        assertEquals("SKILL.md", manifest.files().getFirst().path());
        assertTrue(manifest.toString().indexOf("private/skill.zip") < 0);
        verify(fixture.assets).releaseForUse(
                ACTOR, ASSET_ID, RELEASE_ID, AssetType.SKILL);
    }

    @Test
    void closesAndRejectsStoredBytesWhoseMetadataNoLongerMatchesTheRelease() {
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
    void closesContentWhenTheCanonicalPayloadDoesNotMatchThePinnedReference() {
        Fixture fixture = fixture();
        TrackingInputStream stream = storedContent(fixture);
        when(fixture.specs.read("{\"profile\":\"skill\"}"))
                .thenReturn(spec("b".repeat(64)));

        assertThrows(
                AssetUnavailableException.class,
                () -> fixture.service.open(ACTOR, ASSET_ID, RELEASE_ID));

        assertTrue(stream.closed);
    }

    @Test
    void closesContentWhenTheCanonicalPayloadCannotBeRead() {
        Fixture fixture = fixture();
        TrackingInputStream stream = storedContent(fixture);
        when(fixture.specs.read("{\"profile\":\"skill\"}"))
                .thenThrow(new IllegalArgumentException("invalid payload"));

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
                () -> fixture.service.manifest(ACTOR, ASSET_ID, RELEASE_ID));

        verifyNoInteractions(fixture.storage);
    }

    @Test
    void rejectsAReleaseWhosePackageReferenceIsNotABlob() {
        Fixture fixture = fixture();
        when(fixture.reference.isBlobReference()).thenReturn(false);

        assertThrows(
                AssetUnavailableException.class,
                () -> fixture.service.manifest(ACTOR, ASSET_ID, RELEASE_ID));

        verifyNoInteractions(fixture.storage);
    }

    @Test
    void resolvesCoordinateAndVersionBeforeApplyingTheSameLiveUseCheck() {
        Fixture fixture = fixture();
        AssetIdentity asset = assetIdentity();
        AssetRelease release = mock(AssetRelease.class);
        when(release.getId()).thenReturn(RELEASE_ID);
        when(fixture.identities
                        .findByCoordinate(
                                ORGANIZATION_ID,
                                "support",
                                "triage"))
                .thenReturn(Optional.of(asset));
        when(fixture.releaseRepository
                        .findByAssetIdAndOrganizationIdAndVersionLabel(
                                ASSET_ID,
                                ORGANIZATION_ID,
                                "1.2.0"))
                .thenReturn(Optional.of(release));

        SkillInstallManifest manifest =
                fixture.service.manifest(
                        ACTOR, "Support", "Triage", "1.2.0");

        assertEquals(RELEASE_ID, manifest.releaseId());
        verify(fixture.assets).releaseForUse(
                ACTOR, ASSET_ID, RELEASE_ID, AssetType.SKILL);
    }

    @Test
    void keepsTheInvalidVersionCauseBehindTheOpaqueNotFoundError() {
        Fixture fixture = fixture();
        AssetIdentity asset = assetIdentity();
        when(fixture.identities
                        .findByCoordinate(
                                ORGANIZATION_ID,
                                "support",
                                "triage"))
                .thenReturn(Optional.of(asset));

        AssetNotFoundException failure = assertThrows(
                AssetNotFoundException.class,
                () -> fixture.service.manifest(
                        ACTOR, "support", "triage", "not a version"));

        assertTrue(failure.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void rejectsACoordinateThatResolvesToANonSkillAsset() {
        Fixture fixture = fixture();
        when(fixture.identities
                        .findByCoordinate(
                                ORGANIZATION_ID,
                                "support",
                                "triage"))
                .thenReturn(Optional.of(assetIdentity(AssetType.PROMPT_TEMPLATE)));

        assertThrows(
                AssetNotFoundException.class,
                () -> fixture.service.manifest(
                        ACTOR, "support", "triage", "1.2.0"));
    }

    private static Fixture fixture() {
        AssetRegistryService assets = mock(AssetRegistryService.class);
        AssetIdentityQuery identities = mock(AssetIdentityQuery.class);
        AssetReleaseRepository releaseRepository =
                mock(AssetReleaseRepository.class);
        AssetPayloadReferenceRepository references =
                mock(AssetPayloadReferenceRepository.class);
        SkillPackageSpecReader specs = mock(SkillPackageSpecReader.class);
        SkillPackageStoragePort storage =
                mock(SkillPackageStoragePort.class);
        AssetPayloadReference reference =
                mock(AssetPayloadReference.class);
        when(assets.releaseForUse(
                        ACTOR, ASSET_ID, RELEASE_ID, AssetType.SKILL))
                .thenReturn(release());
        when(specs.read("{\"profile\":\"skill\"}"))
                .thenReturn(spec());
        when(references.findByReleaseIdAndOrganizationId(
                        RELEASE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(reference));
        when(reference.isBlobReference()).thenReturn(true);
        when(reference.getReferenceValue()).thenReturn("private/skill.zip");
        when(reference.getDigest()).thenReturn(PACKAGE_DIGEST);
        when(reference.getContentLength()).thenReturn(7L);
        when(reference.getMediaType()).thenReturn("application/zip");
        SkillReleaseDeliveryService deliveries = new SkillReleaseDeliveryService(
                assets,
                identities,
                releaseRepository,
                references,
                storage);
        return new Fixture(
                new SkillDistributionService(deliveries, specs),
                assets,
                identities,
                releaseRepository,
                references,
                reference,
                specs,
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
        return assetIdentity(AssetType.SKILL);
    }

    private static AssetIdentity assetIdentity(AssetType type) {
        return new AssetIdentity(
                ORGANIZATION_ID,
                ASSET_ID,
                type,
                "support",
                "triage",
                UUID.randomUUID(),
                AssetPortfolioState.DRAFT_ONLY,
                true);
    }

    private static SkillPackageSpec spec() {
        return spec(PACKAGE_DIGEST);
    }

    private static SkillPackageSpec spec(String packageDigest) {
        return new SkillPackageSpec(
                "triage",
                "Triage customer issues",
                "MIT",
                "Claude Code and Codex",
                "Read",
                Map.of("owner", "support"),
                null,
                new SkillPackageSpec.Artifact(
                        packageDigest,
                        7,
                        "application/zip"),
                List.of(new SkillPackageSpec.FileEntry(
                        "SKILL.md",
                        7,
                        "d".repeat(64))));
    }

    private record Fixture(
            SkillDistributionService service,
            AssetRegistryService assets,
            AssetIdentityQuery identities,
            AssetReleaseRepository releaseRepository,
            AssetPayloadReferenceRepository references,
            AssetPayloadReference reference,
            SkillPackageSpecReader specs,
            SkillPackageStoragePort storage) {
    }

    private static TrackingInputStream storedContent(Fixture fixture) {
        TrackingInputStream stream = new TrackingInputStream();
        when(fixture.storage.open("private/skill.zip"))
                .thenReturn(new SkillPackageStoragePort.StoredSkillPackageContent(
                        stream,
                        new SkillPackageStoragePort.StoredSkillPackage(
                                "private/skill.zip",
                                7,
                                "application/zip",
                                PACKAGE_DIGEST)));
        return stream;
    }

    private static final class TrackingInputStream
            extends ByteArrayInputStream {

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
