package com.orgmemory.connectors.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.core.assetregistry.skill.SkillGitHubSourcePort;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;

class GitHubSkillArchiveReaderTests {

    @Test
    void discoversNestedSkillsAndAssignsFilesToTheNearestManifest() throws Exception {
        byte[] archive = tar(Map.of(
                "repo-sha/SKILL.md", manifest("root"),
                "repo-sha/root.txt", "root".getBytes(StandardCharsets.UTF_8),
                "repo-sha/skills/child/SKILL.md", manifest("child"),
                "repo-sha/skills/child/reference.md", "child".getBytes(StandardCharsets.UTF_8)));

        List<SkillGitHubSourcePort.FetchedPackage> packages =
                GitHubSkillArchiveReader.read(archive, "");

        assertEquals(List.of("SKILL.md", "skills/child/SKILL.md"),
                packages.stream().map(SkillGitHubSourcePort.FetchedPackage::path).toList());
        assertEquals(List.of("SKILL.md", "root.txt"), zipEntries(packages.get(0).archive()));
        assertEquals(
                List.of("SKILL.md", "reference.md"),
                zipEntries(packages.get(1).archive()));
    }

    @Test
    void limitsDiscoveryToTheSelectedSubpath() throws Exception {
        byte[] archive = tar(Map.of(
                "repo-sha/other/SKILL.md", manifest("other"),
                "repo-sha/skills/one/SKILL.md", manifest("one")));

        List<SkillGitHubSourcePort.FetchedPackage> packages =
                GitHubSkillArchiveReader.read(archive, "skills");

        assertEquals(List.of("skills/one/SKILL.md"),
                packages.stream().map(SkillGitHubSourcePort.FetchedPackage::path).toList());
    }

    @Test
    void refusesArchiveTraversalBeforePackaging() throws Exception {
        byte[] archive = tar(Map.of(
                "repo-sha/../SKILL.md", manifest("unsafe")));

        BusinessValidationException failure = assertThrows(
                BusinessValidationException.class,
                () -> GitHubSkillArchiveReader.read(archive, ""));

        assertEquals("skill.github-archive-invalid", failure.code());
    }

    @Test
    void rejectsASubpathThatNamesAFileInsteadOfADirectory() throws Exception {
        byte[] archive = tar(Map.of("repo-sha/skills", manifest("not-a-directory")));

        BusinessValidationException failure = assertThrows(
                BusinessValidationException.class,
                () -> GitHubSkillArchiveReader.read(archive, "skills"));

        assertEquals("skill.github-path-invalid", failure.code());
    }

    @Test
    void rejectsARepositoryWithoutSkillManifests() throws Exception {
        byte[] archive = tar(Map.of(
                "repo-sha/README.md", "No packaged Skills".getBytes(StandardCharsets.UTF_8)));

        BusinessValidationException failure = assertThrows(
                BusinessValidationException.class,
                () -> GitHubSkillArchiveReader.read(archive, ""));

        assertEquals("skill.github-no-skills", failure.code());
    }

    @Test
    void rejectsARepositoryWithMoreThanTwentySkills() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (int index = 0; index <= SkillGitHubSourcePort.MAX_SKILLS_PER_IMPORT; index++) {
            files.put("repo-sha/skills/skill-" + index + "/SKILL.md", manifest("skill-" + index));
        }

        BusinessValidationException failure = assertThrows(
                BusinessValidationException.class,
                () -> GitHubSkillArchiveReader.read(tar(files), ""));

        assertEquals("skill.github-too-many-skills", failure.code());
    }

    @Test
    void reportsAnIndividualSkillWhoseZipExceedsTwentyMebibytes() throws Exception {
        byte[] content = new byte[21 * 1024 * 1024];
        new Random(42).nextBytes(content);
        byte[] archive = tar(Map.of(
                "repo-sha/skills/large/SKILL.md", manifest("large"),
                "repo-sha/skills/large/reference.bin", content));

        List<SkillGitHubSourcePort.FetchedPackage> packages =
                GitHubSkillArchiveReader.read(archive, "");

        assertEquals(1, packages.size());
        assertEquals("skill.github-package-too-large", packages.getFirst().errorCode());
    }

    private static byte[] manifest(String name) {
        return ("---\nname: " + name + "\ndescription: A valid repository Skill.\n---\n# " + name)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] tar(Map<String, byte[]> unordered) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        unordered.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> files.put(entry.getKey(), entry.getValue()));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var gzip = new GzipCompressorOutputStream(bytes);
                var tar = new TarArchiveOutputStream(gzip)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                TarArchiveEntry entry = new TarArchiveEntry(file.getKey());
                entry.setSize(file.getValue().length);
                tar.putArchiveEntry(entry);
                tar.write(file.getValue());
                tar.closeArchiveEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static List<String> zipEntries(byte[] archive) throws Exception {
        java.util.ArrayList<String> entries = new java.util.ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        return entries;
    }
}
