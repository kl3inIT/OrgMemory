package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class SkillRegistryServiceTests {

    private static final CurrentActor ACTOR = new CurrentActor(
            UUID.fromString("11111111-1111-4111-8111-111111111111"),
            UUID.fromString("22222222-2222-4222-8222-222222222222"),
            UUID.fromString("33333333-3333-4333-8333-333333333333"),
            "Skill owner",
            "owner@example.test");
    private static final UUID SPACE_ID =
            UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Test
    void refusesUnauthorizedImportsBeforeInspectingOrStoringBytes() {
        SkillPackageStoragePort storage = mock(SkillPackageStoragePort.class);
        AssetRegistryService assets = mock(AssetRegistryService.class);
        doThrow(new OrgMemoryAccessDeniedException("Denied"))
                .when(assets)
                .requireSkillCreate(ACTOR, SPACE_ID);
        SkillRegistryService service =
                new SkillRegistryService(new SkillPackageInspector(), storage, assets);

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> service.importPackage(
                        ACTOR,
                        "support",
                        SPACE_ID,
                        KnowledgeClassification.INTERNAL,
                        1,
                        new ByteArrayInputStream(new byte[] {1})));

        verify(storage, never()).put(any(), any());
    }

    @Test
    void deletesStoredBytesWhenAssetIdentityCreationFails() throws Exception {
        byte[] archive = archive();
        SkillPackageStoragePort storage = mock(SkillPackageStoragePort.class);
        AssetRegistryService assets = mock(AssetRegistryService.class);
        doNothing().when(assets).requireSkillCreate(ACTOR, SPACE_ID);
        when(storage.put(any(), any())).thenAnswer(invocation -> {
            SkillPackageStoragePort.SkillPackageWriteRequest request =
                    invocation.getArgument(0);
            return stored(request);
        });
        when(assets.createValidatedSkillIdentity(
                        eq(ACTOR),
                        eq("support"),
                        eq("support-triage"),
                        eq(SPACE_ID),
                        any(),
                        any()))
                .thenThrow(new AssetConflictException("Duplicate"));
        SkillRegistryService service =
                new SkillRegistryService(new SkillPackageInspector(), storage, assets);

        assertThrows(
                AssetConflictException.class,
                () -> importArchive(service, archive));

        verify(storage).delete(any());
    }

    @Test
    void retainsReferencedBytesWhenAuthorizationProjectionNeedsRetry() throws Exception {
        byte[] archive = archive();
        SkillPackageStoragePort storage = mock(SkillPackageStoragePort.class);
        AssetRegistryService assets = mock(AssetRegistryService.class);
        doNothing().when(assets).requireSkillCreate(ACTOR, SPACE_ID);
        when(storage.put(any(), any())).thenAnswer(invocation -> {
            SkillPackageStoragePort.SkillPackageWriteRequest request =
                    invocation.getArgument(0);
            return stored(request);
        });
        UUID assetId = UUID.randomUUID();
        when(assets.createValidatedSkillIdentity(
                        any(), any(), any(), any(), any(), any()))
                .thenReturn(assetId);
        when(assets.projectCreated(ACTOR, assetId))
                .thenThrow(new AssetUnavailableException("Projection pending"));
        SkillRegistryService service =
                new SkillRegistryService(new SkillPackageInspector(), storage, assets);

        assertThrows(
                AssetUnavailableException.class,
                () -> importArchive(service, archive));

        verify(storage, never()).delete(any());
    }

    private static AssetView importArchive(
            SkillRegistryService service, byte[] archive) {
        return service.importPackage(
                ACTOR,
                "support",
                SPACE_ID,
                KnowledgeClassification.INTERNAL,
                archive.length,
                new ByteArrayInputStream(archive));
    }

    private static SkillPackageStoragePort.StoredSkillPackage stored(
            SkillPackageStoragePort.SkillPackageWriteRequest request) {
        return new SkillPackageStoragePort.StoredSkillPackage(
                "assets/skills/"
                        + request.organizationId()
                        + "/"
                        + request.packageId()
                        + ".zip",
                request.contentLength(),
                "application/zip",
                request.expectedSha256());
    }

    private static byte[] archive() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("support-triage/SKILL.md"));
            zip.write("""
                    ---
                    name: support-triage
                    description: Triage support requests using approved guidance.
                    ---
                    # Support triage
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
