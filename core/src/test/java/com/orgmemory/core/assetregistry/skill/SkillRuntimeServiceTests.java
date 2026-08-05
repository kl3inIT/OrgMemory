package com.orgmemory.core.assetregistry.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.assetregistry.consumption.AssetPublicationMode;
import com.orgmemory.core.assetregistry.skilldelivery.SkillReleaseDeliveryQuery;
import com.orgmemory.core.assetregistry.skilldelivery.SkillReleaseSummary;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.UserRole;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class SkillRuntimeServiceTests {

    private static final UUID ASSET_ID = UUID.fromString(
            "91000000-0000-0000-0000-000000000001");
    private static final UUID RELEASE_ID = UUID.fromString(
            "91000000-0000-0000-0000-000000000002");
    private static final CurrentActor ACTOR = new CurrentActor(
            UUID.fromString("91000000-0000-0000-0000-000000000003"),
            UUID.fromString("91000000-0000-0000-0000-000000000004"),
            null,
            "Agent user",
            "agent@example.test",
            UserRole.EMPLOYEE);

    @Test
    void searchesOnlyThroughTheActorScopedDeliveryBoundary() {
        Fixture fixture = fixture();
        when(fixture.deliveries.search(ACTOR, "incident", 5))
                .thenReturn(List.of(new SkillReleaseSummary(
                        ASSET_ID,
                        RELEASE_ID,
                        "support",
                        "incident-response",
                        "1.0.0",
                        "Incident response",
                        "Coordinate incidents",
                        "a".repeat(64))));

        List<SkillRuntimeOperations.SkillSummary> result =
                fixture.service.search(ACTOR, "incident", 5);

        assertEquals(1, result.size());
        assertEquals("support/incident-response", result.getFirst().coordinate());
        assertEquals(RELEASE_ID, result.getFirst().releaseId());
        verify(fixture.deliveries).search(ACTOR, "incident", 5);
    }

    @Test
    void activatesExactSkillInstructionsAndListsResources() throws Exception {
        Fixture fixture = fixture();
        Map<String, byte[]> files = files(
                "SKILL.md", "---\nname: incident-response\ndescription: Help\n---\n\nFollow the runbook.",
                "references/runbook.md", "# Runbook\nEscalate safely.");
        openedPackage(fixture, files, "incident-response/");

        SkillRuntimeOperations.ActivatedSkill result =
                fixture.service.activate(ACTOR, ASSET_ID, RELEASE_ID);

        assertEquals("support/incident-response", result.skill().coordinate());
        assertEquals("---\nname: incident-response\ndescription: Help\n---\n\nFollow the runbook.",
                result.instructions());
        assertEquals(List.of("references/runbook.md"), result.resources());
        verify(fixture.distribution).open(ACTOR, ASSET_ID, RELEASE_ID);
    }

    @Test
    void readsOnlyAnExactDeclaredUtf8Resource() throws Exception {
        Fixture fixture = fixture();
        Map<String, byte[]> files = files(
                "SKILL.md", "instructions",
                "references/runbook.md", "# Safe runbook");
        openedPackage(fixture, files, "");

        SkillRuntimeOperations.SkillResource result = fixture.service.readResource(
                ACTOR, ASSET_ID, RELEASE_ID, "references/runbook.md");

        assertEquals("# Safe runbook", result.content());
        assertEquals("references/runbook.md", result.path());
    }

    @Test
    void rejectsTraversalBeforeOpeningThePackage() {
        Fixture fixture = fixture();

        assertThrows(IllegalArgumentException.class, () -> fixture.service.readResource(
                ACTOR, ASSET_ID, RELEASE_ID, "../secret.txt"));
    }

    @Test
    void rejectsContentThatDoesNotMatchTheInspectedManifest() throws Exception {
        Fixture fixture = fixture();
        Map<String, byte[]> files = files("SKILL.md", "changed");
        byte[] archive = zip(files, "");
        SkillInstallManifest manifest = manifest(Map.of(
                "SKILL.md", "expected".getBytes(StandardCharsets.UTF_8)));
        when(fixture.distribution.open(ACTOR, ASSET_ID, RELEASE_ID))
                .thenReturn(new SkillPackageContent(
                        manifest,
                        "incident-response.zip",
                        new ByteArrayInputStream(archive)));

        assertThrows(AssetUnavailableException.class, () ->
                fixture.service.activate(ACTOR, ASSET_ID, RELEASE_ID));
    }

    @Test
    void rejectsRuntimeTextBeyondTheIndependentModelContextLimit() throws Exception {
        Fixture fixture = fixture();
        byte[] oversized = new byte[SkillRuntimeService.MAX_RUNTIME_TEXT_BYTES + 1];
        Map<String, byte[]> files = Map.of("SKILL.md", oversized);
        openedPackage(fixture, files, "");

        assertThrows(AssetUnavailableException.class, () ->
                fixture.service.activate(ACTOR, ASSET_ID, RELEASE_ID));
    }

    @Test
    void rejectsBinaryOrMalformedUtf8Resources() throws Exception {
        Fixture fixture = fixture();
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("SKILL.md", "instructions".getBytes(StandardCharsets.UTF_8));
        files.put("references/binary.dat", new byte[] {(byte) 0xc3, 0x28});
        openedPackage(fixture, files, "incident-response/");

        assertThrows(AssetUnavailableException.class, () ->
                fixture.service.readResource(
                        ACTOR,
                        ASSET_ID,
                        RELEASE_ID,
                        "references/binary.dat"));
    }

    private static Fixture fixture() {
        SkillReleaseDeliveryQuery deliveries = mock(SkillReleaseDeliveryQuery.class);
        SkillDistributionOperations distribution = mock(SkillDistributionOperations.class);
        return new Fixture(
                new SkillRuntimeService(deliveries, distribution),
                deliveries,
                distribution);
    }

    private static void openedPackage(
            Fixture fixture, Map<String, byte[]> files, String root) throws Exception {
        when(fixture.distribution.open(ACTOR, ASSET_ID, RELEASE_ID))
                .thenReturn(new SkillPackageContent(
                        manifest(files),
                        "incident-response.zip",
                        new ByteArrayInputStream(zip(files, root))));
    }

    private static SkillInstallManifest manifest(Map<String, byte[]> files) {
        List<SkillInstallManifest.File> manifestFiles = files.entrySet().stream()
                .map(entry -> new SkillInstallManifest.File(
                        entry.getKey(), entry.getValue().length, digest(entry.getValue())))
                .toList();
        return new SkillInstallManifest(
                ASSET_ID,
                RELEASE_ID,
                "support",
                "incident-response",
                "support/incident-response",
                "1.0.0",
                AssetPublicationMode.DIRECT,
                "Incident response",
                "Coordinate incidents",
                "a".repeat(64),
                "b".repeat(64),
                100,
                "application/zip",
                "MIT",
                "OrgMemory Assistant",
                "Shell(git:*)",
                Map.of(),
                manifestFiles);
    }

    private static Map<String, byte[]> files(String... values) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            files.put(values[index], values[index + 1].getBytes(StandardCharsets.UTF_8));
        }
        return files;
    }

    private static byte[] zip(Map<String, byte[]> files, String root) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(root + file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private record Fixture(
            SkillRuntimeService service,
            SkillReleaseDeliveryQuery deliveries,
            SkillDistributionOperations distribution) {
    }
}
