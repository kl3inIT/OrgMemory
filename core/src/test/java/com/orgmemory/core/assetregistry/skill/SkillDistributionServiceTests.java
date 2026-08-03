package com.orgmemory.core.assetregistry.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.assetregistry.consumption.AssetAvailability;
import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.assetregistry.consumption.AssetPublicationMode;
import com.orgmemory.core.assetregistry.skilldelivery.SkillReleaseContent;
import com.orgmemory.core.assetregistry.skilldelivery.SkillReleaseDeliveryQuery;
import com.orgmemory.core.assetregistry.skilldelivery.SkillReleaseDescriptor;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageArtifact;
import com.orgmemory.core.organization.CurrentActor;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
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
    void returnsAnExactManifestWithoutExposingAStorageReference() {
        Fixture fixture = fixture();

        SkillInstallManifest manifest =
                fixture.service.manifest(ACTOR, ASSET_ID, RELEASE_ID);

        assertEquals("support/triage", manifest.coordinate());
        assertEquals("1.2.0", manifest.version());
        assertEquals(PACKAGE_DIGEST, manifest.packageDigest());
        assertEquals("SKILL.md", manifest.files().getFirst().path());
        assertTrue(manifest.toString().indexOf("private/skill.zip") < 0);
        verify(fixture.deliveries).describe(ACTOR, ASSET_ID, RELEASE_ID);
    }

    @Test
    void resolvesTheCoordinateThroughTheParentDeliveryCapability() {
        Fixture fixture = fixture();
        when(fixture.deliveries.describe(ACTOR, "support", "triage", "1.2.0"))
                .thenReturn(descriptor());

        SkillInstallManifest manifest = fixture.service.manifest(
                ACTOR, "support", "triage", "1.2.0");

        assertEquals(RELEASE_ID, manifest.releaseId());
        verify(fixture.deliveries).describe(ACTOR, "support", "triage", "1.2.0");
    }

    @Test
    void closesContentWhenTheCanonicalPayloadDoesNotMatchThePinnedReference() {
        Fixture fixture = fixture();
        TrackingInputStream stream = openedContent(fixture);
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
        TrackingInputStream stream = openedContent(fixture);
        when(fixture.specs.read("{\"profile\":\"skill\"}"))
                .thenThrow(new IllegalArgumentException("invalid payload"));

        assertThrows(
                AssetUnavailableException.class,
                () -> fixture.service.open(ACTOR, ASSET_ID, RELEASE_ID));

        assertTrue(stream.closed);
    }

    private static Fixture fixture() {
        SkillReleaseDeliveryQuery deliveries = mock(SkillReleaseDeliveryQuery.class);
        SkillPackageSpecReader specs = mock(SkillPackageSpecReader.class);
        when(deliveries.describe(ACTOR, ASSET_ID, RELEASE_ID))
                .thenReturn(descriptor());
        when(specs.read("{\"profile\":\"skill\"}"))
                .thenReturn(spec(PACKAGE_DIGEST));
        return new Fixture(
                new SkillDistributionService(deliveries, specs),
                deliveries,
                specs);
    }

    private static TrackingInputStream openedContent(Fixture fixture) {
        TrackingInputStream stream = new TrackingInputStream();
        when(fixture.deliveries.open(ACTOR, ASSET_ID, RELEASE_ID))
                .thenReturn(new SkillReleaseContent(descriptor(), stream));
        return stream;
    }

    private static SkillReleaseDescriptor descriptor() {
        return new SkillReleaseDescriptor(
                release(),
                new SkillPackageArtifact(
                        PACKAGE_DIGEST, 7, SkillPackageArtifact.ZIP_MEDIA_TYPE));
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
                        SkillPackageArtifact.ZIP_MEDIA_TYPE),
                List.of(new SkillPackageSpec.FileEntry(
                        "SKILL.md",
                        7,
                        "d".repeat(64))));
    }

    private record Fixture(
            SkillDistributionService service,
            SkillReleaseDeliveryQuery deliveries,
            SkillPackageSpecReader specs) {
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
