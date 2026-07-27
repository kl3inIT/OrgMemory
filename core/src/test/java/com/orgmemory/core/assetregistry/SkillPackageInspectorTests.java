package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

class SkillPackageInspectorTests {

    private final SkillPackageInspector inspector = new SkillPackageInspector();

    @Test
    void inspectsOnePortableSkillWithoutExtractingIt() throws Exception {
        byte[] archive = archive(Map.of(
                "support-triage/SKILL.md",
                """
                ---
                name: support-triage
                description: Triage support tickets when a new request arrives.
                license: Proprietary
                compatibility: Agent Skills compatible clients
                metadata:
                  owner: support-operations
                  version: "1.0"
                allowed-tools: Read
                ---
                # Support triage
                """.getBytes(StandardCharsets.UTF_8),
                "support-triage/references/policy.md",
                "Policy".getBytes(StandardCharsets.UTF_8)));

        try (var inspected =
                inspector.inspect(new ByteArrayInputStream(archive), archive.length)) {
            assertEquals("support-triage", inspected.metadata().name());
            assertEquals("support-operations", inspected.metadata().metadata().get("owner"));
            assertEquals(2, inspected.files().size());
            assertEquals("SKILL.md", inspected.files().getFirst().path());
            assertEquals(64, inspected.sha256().length());
            assertTrue(inspected.contentLength() > 0);
        }
    }

    @Test
    void rejectsTraversalCaseCollisionsAndSymbolicLinks() throws Exception {
        byte[] traversal = archive(Map.of(
                "../SKILL.md",
                validSkill("unsafe").getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                SkillPackageValidationException.class,
                () -> inspector.inspect(
                        new ByteArrayInputStream(traversal), traversal.length));

        Map<String, byte[]> colliding = new LinkedHashMap<>();
        colliding.put(
                "support/SKILL.md",
                validSkill("support").getBytes(StandardCharsets.UTF_8));
        colliding.put("support/readme.md", new byte[] {1});
        colliding.put("support/README.md", new byte[] {2});
        byte[] collision = archive(colliding);
        assertThrows(
                SkillPackageValidationException.class,
                () -> inspector.inspect(
                        new ByteArrayInputStream(collision), collision.length));

        byte[] symlink = symbolicLinkArchive();
        assertThrows(
                SkillPackageValidationException.class,
                () -> inspector.inspect(new ByteArrayInputStream(symlink), symlink.length));
    }

    @Test
    void rejectsInvalidFrontmatterAndOversizedSkillMarkdown() throws Exception {
        byte[] duplicateYaml = archive(Map.of(
                "support/SKILL.md",
                """
                ---
                name: support
                name: replacement
                description: Duplicate keys are forbidden.
                ---
                """.getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                SkillPackageValidationException.class,
                () -> inspector.inspect(
                        new ByteArrayInputStream(duplicateYaml), duplicateYaml.length));

        String oversized = validSkill("support")
                + "x".repeat(SkillPackageInspector.MAX_SKILL_MD_BYTES);
        byte[] oversizedArchive = archive(Map.of(
                "support/SKILL.md", oversized.getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                SkillPackageValidationException.class,
                () -> inspector.inspect(
                        new ByteArrayInputStream(oversizedArchive), oversizedArchive.length));

        byte[] invalidUtf8 = archive(Map.of(
                "support/SKILL.md", new byte[] {(byte) 0xC3, (byte) 0x28}));
        assertThrows(
                SkillPackageValidationException.class,
                () -> inspector.inspect(
                        new ByteArrayInputStream(invalidUtf8), invalidUtf8.length));

        assertThrows(
                SkillPackageValidationException.class,
                () -> inspector.inspect(
                        new ByteArrayInputStream(new byte[] {1}),
                        SkillPackageInspector.MAX_ARCHIVE_BYTES + 1));
    }

    @Test
    void rejectsMismatchedDirectoryAndDeclaredLength() throws Exception {
        byte[] wrongDirectory = archive(Map.of(
                "another/SKILL.md",
                validSkill("support").getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                SkillPackageValidationException.class,
                () -> inspector.inspect(
                        new ByteArrayInputStream(wrongDirectory), wrongDirectory.length));

        byte[] valid = archive(Map.of(
                "support/SKILL.md",
                validSkill("support").getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                SkillPackageValidationException.class,
                () -> inspector.inspect(
                        new ByteArrayInputStream(valid), valid.length + 1L));
    }

    @Test
    void rejectsEntriesOutsideTheSingleTopLevelSkillDirectory() throws Exception {
        byte[] archive = archiveWithEmptyDirectory(
                "unrelated/",
                Map.of(
                        "support/SKILL.md",
                        validSkill("support").getBytes(StandardCharsets.UTF_8)));

        assertThrows(
                SkillPackageValidationException.class,
                () -> inspector.inspect(
                        new ByteArrayInputStream(archive), archive.length));
    }

    private static String validSkill(String name) {
        return """
                ---
                name: %s
                description: Use this Skill for a bounded test workflow.
                ---
                # Test
                """.formatted(name);
    }

    private static byte[] archive(Map<String, byte[]> files) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(output)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipArchiveEntry entry = new ZipArchiveEntry(file.getKey());
                zip.putArchiveEntry(entry);
                zip.write(file.getValue());
                zip.closeArchiveEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] archiveWithEmptyDirectory(
            String directory, Map<String, byte[]> files) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(output)) {
            ZipArchiveEntry directoryEntry = new ZipArchiveEntry(directory);
            zip.putArchiveEntry(directoryEntry);
            zip.closeArchiveEntry();
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipArchiveEntry entry = new ZipArchiveEntry(file.getKey());
                zip.putArchiveEntry(entry);
                zip.write(file.getValue());
                zip.closeArchiveEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] symbolicLinkArchive() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(output)) {
            ZipArchiveEntry skill = new ZipArchiveEntry("support/SKILL.md");
            zip.putArchiveEntry(skill);
            zip.write(validSkill("support").getBytes(StandardCharsets.UTF_8));
            zip.closeArchiveEntry();

            ZipArchiveEntry link = new ZipArchiveEntry("support/references/latest.md");
            link.setUnixMode(0120777);
            zip.putArchiveEntry(link);
            zip.write("../secret.md".getBytes(StandardCharsets.UTF_8));
            zip.closeArchiveEntry();
        }
        return output.toByteArray();
    }
}
