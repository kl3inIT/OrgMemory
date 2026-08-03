package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.api.AssetPortfolioState;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageArtifact;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackagePayloadPolicy;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageUpload;
import com.orgmemory.core.assetregistry.skillstorage.SkillPackageStoragePort;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkillPackageAssetServiceTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("86000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("86000000-0000-0000-0000-000000000002");
    private static final UUID SPACE_ID =
            UUID.fromString("86000000-0000-0000-0000-000000000003");
    private static final UUID ASSET_ID =
            UUID.fromString("86000000-0000-0000-0000-000000000004");
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID,
            ORGANIZATION_ID,
            null,
            "Skill editor",
            "skill.editor@example.test");
    private static final SkillPackageArtifact ARTIFACT =
            new SkillPackageArtifact("a".repeat(64), 3, "application/zip");

    @Test
    void rejectsAnInvalidImportPayloadBeforeWritingStorage() {
        Fixture fixture = fixture();
        rejectPayload(fixture.policy);

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.importPackage(
                        ACTOR,
                        "support",
                        SPACE_ID,
                        KnowledgeClassification.INTERNAL,
                        upload()));

        verifyNoInteractions(fixture.storage);
    }

    @Test
    void rejectsAnInvalidReplacementPayloadBeforeWritingStorage() {
        Fixture fixture = fixture();
        when(fixture.assets.get(ACTOR, ASSET_ID)).thenReturn(skillView());
        rejectPayload(fixture.policy);

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.replacePackage(
                        ACTOR,
                        ASSET_ID,
                        4,
                        upload()));

        verifyNoInteractions(fixture.storage);
    }

    private static Fixture fixture() {
        SkillPackageStoragePort storage = mock(SkillPackageStoragePort.class);
        SkillPackagePayloadPolicy policy = mock(SkillPackagePayloadPolicy.class);
        AssetRegistryService assets = mock(AssetRegistryService.class);
        SkillPackageSupersessionCleanupService cleanup =
                mock(SkillPackageSupersessionCleanupService.class);
        return new Fixture(
                new SkillPackageAssetService(storage, policy, assets, cleanup),
                storage,
                policy,
                assets);
    }

    private static void rejectPayload(SkillPackagePayloadPolicy policy) {
        doThrow(new IllegalArgumentException("invalid payload"))
                .when(policy)
                .validate("{}", ARTIFACT);
    }

    private static SkillPackageUpload upload() {
        return new SkillPackageUpload(
                UUID.randomUUID(),
                "support-triage",
                "Support triage",
                "Triage support requests",
                "2",
                "{}",
                ARTIFACT,
                Map.of(),
                new ByteArrayInputStream(new byte[] {1, 2, 3}));
    }

    private static AssetView skillView() {
        return new AssetView(
                ASSET_ID,
                AssetType.SKILL,
                "support",
                "support-triage",
                SPACE_ID,
                AssetPortfolioState.DRAFT_ONLY,
                true,
                new AssetView.Draft(
                        UUID.randomUUID(),
                        4,
                        "Support triage",
                        "Triage support requests",
                        "INTERNAL",
                        "2",
                        "{}",
                        USER_ID,
                        Instant.parse("2026-08-03T00:00:00Z")),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of());
    }

    private record Fixture(
            SkillPackageAssetService service,
            SkillPackageStoragePort storage,
            SkillPackagePayloadPolicy policy,
            AssetRegistryService assets) {
    }
}
