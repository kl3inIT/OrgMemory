package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.api.AssetConflictException;
import com.orgmemory.core.assetregistry.api.AssetPortfolioState;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
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

    @Test
    void deletesStoredBytesWhenAssetIdentityCreationFails() {
        Fixture fixture = fixture();
        when(fixture.storage.put(any(), any())).thenReturn(stored());
        when(fixture.assets.createValidatedSkillIdentity(
                        eq(ACTOR),
                        eq("support"),
                        eq("support-triage"),
                        eq(SPACE_ID),
                        any(),
                        any()))
                .thenThrow(new AssetConflictException("Duplicate"));

        assertThrows(
                AssetConflictException.class,
                () -> fixture.service.importPackage(
                        ACTOR,
                        "support",
                        SPACE_ID,
                        KnowledgeClassification.INTERNAL,
                        upload()));

        verify(fixture.storage).delete("assets/skills/package.zip");
    }

    @Test
    void retainsReferencedBytesWhenAuthorizationProjectionNeedsRetry() {
        Fixture fixture = fixture();
        when(fixture.storage.put(any(), any())).thenReturn(stored());
        when(fixture.assets.createValidatedSkillIdentity(
                        any(), any(), any(), any(), any(), any()))
                .thenReturn(ASSET_ID);
        when(fixture.assets.projectCreated(ACTOR, ASSET_ID))
                .thenThrow(new AssetUnavailableException("Projection pending"));

        assertThrows(
                AssetUnavailableException.class,
                () -> fixture.service.importPackage(
                        ACTOR,
                        "support",
                        SPACE_ID,
                        KnowledgeClassification.INTERNAL,
                        upload()));

        verify(fixture.storage, never()).delete(any());
    }

    @Test
    void replacementCleansTheDurableSupersessionAfterTheSwap() {
        Fixture fixture = fixture();
        UUID supersessionId = UUID.randomUUID();
        when(fixture.assets.get(ACTOR, ASSET_ID)).thenReturn(skillView());
        when(fixture.storage.put(any(), any())).thenReturn(stored());
        when(fixture.assets.replaceValidatedSkillDraft(
                        eq(ACTOR), eq(ASSET_ID), eq(4L), any(), any()))
                .thenReturn(new SkillDraftReplacement(skillView(), supersessionId));

        UUID replacedId = fixture.service.replacePackage(
                ACTOR, ASSET_ID, 4, upload());

        org.junit.jupiter.api.Assertions.assertEquals(ASSET_ID, replacedId);
        verify(fixture.assets, times(1)).requireSkillEdit(ACTOR, ASSET_ID);
        verify(fixture.cleanup).cleanup(supersessionId);
        verify(fixture.storage, never()).delete(any());
    }

    @Test
    void replacementDeletesTheNewObjectWhenTheDatabaseSwapFails() {
        Fixture fixture = fixture();
        when(fixture.assets.get(ACTOR, ASSET_ID)).thenReturn(skillView());
        when(fixture.storage.put(any(), any())).thenReturn(stored());
        when(fixture.assets.replaceValidatedSkillDraft(
                        eq(ACTOR), eq(ASSET_ID), eq(4L), any(), any()))
                .thenThrow(new AssetConflictException("Changed"));

        assertThrows(
                AssetConflictException.class,
                () -> fixture.service.replacePackage(
                        ACTOR, ASSET_ID, 4, upload()));

        verify(fixture.storage).delete("assets/skills/package.zip");
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
                assets,
                cleanup);
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
                USER_ID,
                com.orgmemory.core.assetregistry.api.AssetSharingState.PRIVATE,
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

    private static SkillPackageStoragePort.StoredSkillPackage stored() {
        return new SkillPackageStoragePort.StoredSkillPackage(
                "assets/skills/package.zip",
                ARTIFACT.contentLength(),
                ARTIFACT.mediaType(),
                ARTIFACT.sha256());
    }

    private record Fixture(
            SkillPackageAssetService service,
            SkillPackageStoragePort storage,
            SkillPackagePayloadPolicy policy,
            AssetRegistryService assets,
            SkillPackageSupersessionCleanupService cleanup) {
    }
}
