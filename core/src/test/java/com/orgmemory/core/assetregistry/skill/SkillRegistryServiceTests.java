package com.orgmemory.core.assetregistry.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.api.AssetNotFoundException;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageAssetCommand;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageUpload;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SkillRegistryServiceTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("88000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("88000000-0000-0000-0000-000000000002");
    private static final UUID SPACE_ID =
            UUID.fromString("88000000-0000-0000-0000-000000000003");
    private static final UUID ASSET_ID =
            UUID.fromString("88000000-0000-0000-0000-000000000004");
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID,
            ORGANIZATION_ID,
            null,
            "Skill editor",
            "skill.editor@example.test");

    @Test
    void refusesUnauthorizedImportsBeforeReadingPackageBytes() {
        SkillPackageAssetCommand packages = mock(SkillPackageAssetCommand.class);
        doThrow(new AssetNotFoundException())
                .when(packages)
                .requireCreate(ACTOR, SPACE_ID);
        SkillRegistryService service = service(packages);

        assertThrows(
                AssetNotFoundException.class,
                () -> service.importPackage(
                        ACTOR,
                        "support",
                        SPACE_ID,
                        KnowledgeClassification.INTERNAL,
                        1,
                        new UnreadableInputStream()));
    }

    @Test
    void importsOneCanonicalPackageThroughTheParentCapability() throws Exception {
        SkillPackageAssetCommand packages = mock(SkillPackageAssetCommand.class);
        when(packages.importPackage(
                        eq(ACTOR),
                        eq("support"),
                        eq(SPACE_ID),
                        eq(KnowledgeClassification.INTERNAL),
                        any(SkillPackageUpload.class)))
                .thenReturn(ASSET_ID);
        SkillRegistryService service = service(packages);
        byte[] archive = archive();

        UUID importedId = service.importPackage(
                ACTOR,
                "support",
                SPACE_ID,
                KnowledgeClassification.INTERNAL,
                archive.length,
                new ByteArrayInputStream(archive));

        assertEquals(ASSET_ID, importedId);
        ArgumentCaptor<SkillPackageUpload> upload =
                ArgumentCaptor.forClass(SkillPackageUpload.class);
        verify(packages).importPackage(
                eq(ACTOR),
                eq("support"),
                eq(SPACE_ID),
                eq(KnowledgeClassification.INTERNAL),
                upload.capture());
        assertEquals("support-triage", upload.getValue().slug());
        assertEquals("2", upload.getValue().schemaVersion());
        assertEquals("application/zip", upload.getValue().artifact().mediaType());
        assertTrue(upload.getValue().payload().contains("\"name\":\"support-triage\""));
    }

    @Test
    void inspectionIsStatelessAndReturnsOnlyValidatedFacts() throws Exception {
        SkillPackageAssetCommand packages = mock(SkillPackageAssetCommand.class);
        SkillRegistryService service = service(packages);
        byte[] archive = archive();

        SkillPackageInspection inspection = service.inspectPackage(
                ACTOR, archive.length, new ByteArrayInputStream(archive));

        assertEquals("support-triage", inspection.name());
        assertEquals("SKILL.md", inspection.files().getFirst().path());
        assertTrue(inspection.instructions().contains("# Support triage"));
    }

    @Test
    void replacementAuthorizesBeforeReadingAndRoutesTheCanonicalUpload()
            throws Exception {
        SkillPackageAssetCommand packages = mock(SkillPackageAssetCommand.class);
        when(packages.requireEdit(ACTOR, ASSET_ID))
                .thenReturn(KnowledgeClassification.INTERNAL);
        when(packages.replacePackage(
                        eq(ACTOR),
                        eq(ASSET_ID),
                        eq(7L),
                        any(SkillPackageUpload.class)))
                .thenReturn(ASSET_ID);
        SkillRegistryService service = service(packages);
        byte[] archive = archive();

        UUID replacedId = service.replacePackage(
                ACTOR,
                ASSET_ID,
                7,
                archive.length,
                new ByteArrayInputStream(archive));

        assertEquals(ASSET_ID, replacedId);
        verify(packages).requireEdit(ACTOR, ASSET_ID);
        verify(packages).replacePackage(
                eq(ACTOR), eq(ASSET_ID), eq(7L), any(SkillPackageUpload.class));
    }

    @Test
    void refusesUnauthorizedReplacementBeforeReadingPackageBytes() {
        SkillPackageAssetCommand packages = mock(SkillPackageAssetCommand.class);
        doThrow(new AssetNotFoundException())
                .when(packages)
                .requireEdit(ACTOR, ASSET_ID);
        SkillRegistryService service = service(packages);

        assertThrows(
                AssetNotFoundException.class,
                () -> service.replacePackage(
                        ACTOR,
                        ASSET_ID,
                        7,
                        1,
                        new UnreadableInputStream()));
    }

    private static SkillRegistryService service(
            SkillPackageAssetCommand packages) {
        return new SkillRegistryService(new SkillPackageInspector(), packages);
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

    private static final class UnreadableInputStream extends InputStream {

        @Override
        public int read() {
            throw new AssertionError("package bytes were read before authorization");
        }
    }
}
