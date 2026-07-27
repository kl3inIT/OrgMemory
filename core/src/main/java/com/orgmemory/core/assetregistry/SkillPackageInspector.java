package com.orgmemory.core.assetregistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@Component
class SkillPackageInspector {

    static final long MAX_ARCHIVE_BYTES = 20L * 1024 * 1024;
    static final long MAX_UNPACKED_BYTES = 50L * 1024 * 1024;
    static final int MAX_FILES = 300;
    static final int MAX_SKILL_MD_BYTES = 512 * 1024;
    private static final Set<String> FRONTMATTER_FIELDS =
            Set.of("name", "description", "license", "compatibility", "metadata", "allowed-tools");

    StagedSkillPackage inspect(InputStream content, long declaredLength) {
        Objects.requireNonNull(content, "content");
        if (declaredLength <= 0 || declaredLength > MAX_ARCHIVE_BYTES) {
            throw new SkillPackageValidationException(
                    "Skill package must be a ZIP no larger than 20 MiB");
        }
        Path staged = null;
        try {
            staged = Files.createTempFile("orgmemory-skill-", ".zip");
            MessageDigest archiveDigest = sha256();
            long copied;
            try (OutputStream output = Files.newOutputStream(staged);
                    DigestInputStream digested = new DigestInputStream(content, archiveDigest)) {
                copied = copyBounded(digested, output, MAX_ARCHIVE_BYTES);
            }
            if (copied != declaredLength) {
                throw new IllegalArgumentException(
                        "Skill package content length does not match the declared length");
            }
            InspectedPackage inspected = inspectZip(staged);
            return new StagedSkillPackage(
                    staged,
                    copied,
                    java.util.HexFormat.of().formatHex(archiveDigest.digest()),
                    inspected.metadata(),
                    inspected.files());
        } catch (SkillPackageValidationException failure) {
            delete(staged);
            throw failure;
        } catch (IllegalArgumentException failure) {
            delete(staged);
            throw new SkillPackageValidationException(failure.getMessage(), failure);
        } catch (IOException failure) {
            delete(staged);
            throw new SkillPackageValidationException(
                    "Skill package ZIP could not be read", failure);
        }
    }

    private InspectedPackage inspectZip(Path staged) throws IOException {
        try (ZipFile zip = ZipFile.builder().setPath(staged).get()) {
            List<ZipArchiveEntry> archiveEntries = new ArrayList<>();
            List<ZipArchiveEntry> fileEntries = new ArrayList<>();
            Set<String> paths = new HashSet<>();
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                String path = entry.getName();
                requireArchivePath(path);
                String collisionKey = path.toLowerCase(Locale.ROOT);
                if (!paths.add(collisionKey)) {
                    throw new IllegalArgumentException(
                            "Skill package contains duplicate or case-colliding paths");
                }
                if (entry.isUnixSymlink()) {
                    throw new IllegalArgumentException(
                            "Skill package must not contain symbolic links");
                }
                if (!zip.canReadEntryData(entry)) {
                    throw new IllegalArgumentException(
                            "Skill package contains an encrypted or unsupported ZIP entry");
                }
                archiveEntries.add(entry);
                if (!entry.isDirectory()) {
                    fileEntries.add(entry);
                    if (fileEntries.size() > MAX_FILES) {
                        throw new IllegalArgumentException(
                                "Skill package contains more than 300 files");
                    }
                }
            }
            if (fileEntries.isEmpty()) {
                throw new IllegalArgumentException("Skill package contains no files");
            }

            String root = skillRoot(archiveEntries);
            List<SkillPackageSpec.FileEntry> manifest = new ArrayList<>();
            byte[] skillMarkdown = null;
            long totalBytes = 0;
            for (ZipArchiveEntry entry : fileEntries) {
                String relativePath = relativePath(entry.getName(), root);
                MessageDigest fileDigest = sha256();
                long entryBytes;
                ByteArrayOutputStream skillBuffer =
                        relativePath.equals("SKILL.md") ? new ByteArrayOutputStream() : null;
                try (InputStream entryContent = zip.getInputStream(entry);
                        DigestInputStream digested =
                                new DigestInputStream(entryContent, fileDigest)) {
                    entryBytes = readEntry(
                            digested,
                            skillBuffer,
                            MAX_UNPACKED_BYTES - totalBytes,
                            relativePath.equals("SKILL.md")
                                    ? MAX_SKILL_MD_BYTES
                                    : MAX_UNPACKED_BYTES);
                }
                totalBytes += entryBytes;
                manifest.add(new SkillPackageSpec.FileEntry(
                        relativePath,
                        entryBytes,
                        java.util.HexFormat.of().formatHex(fileDigest.digest())));
                if (skillBuffer != null) {
                    skillMarkdown = skillBuffer.toByteArray();
                }
            }
            if (skillMarkdown == null) {
                throw new IllegalArgumentException("Skill package requires SKILL.md");
            }
            SkillMetadata metadata = parseSkillMarkdown(skillMarkdown, root);
            manifest.sort(Comparator.comparing(SkillPackageSpec.FileEntry::path));
            return new InspectedPackage(metadata, List.copyOf(manifest));
        }
    }

    private static String skillRoot(List<ZipArchiveEntry> entries) {
        List<String> skillPaths = entries.stream()
                .map(ZipArchiveEntry::getName)
                .filter(path -> path.equals("SKILL.md") || path.endsWith("/SKILL.md"))
                .toList();
        if (skillPaths.size() != 1) {
            throw new IllegalArgumentException(
                    "Skill package must contain exactly one SKILL.md");
        }
        String skillPath = skillPaths.getFirst();
        if (skillPath.equals("SKILL.md")) {
            return "";
        }
        String root = skillPath.substring(0, skillPath.length() - "/SKILL.md".length());
        if (root.contains("/")) {
            throw new IllegalArgumentException(
                    "Skill package directory must be at the archive root");
        }
        String prefix = root + "/";
        if (entries.stream().anyMatch(entry -> !entry.getName().startsWith(prefix))) {
            throw new IllegalArgumentException(
                    "Skill package must contain exactly one skill directory");
        }
        return root;
    }

    private static String relativePath(String archivePath, String root) {
        String relative = root.isEmpty()
                ? archivePath
                : archivePath.substring(root.length() + 1);
        if (!isSafeRelativePath(relative)) {
            throw new IllegalArgumentException("Skill package contains an unsafe relative path");
        }
        return relative;
    }

    static boolean isSafeRelativePath(String value) {
        if (value == null
                || value.isBlank()
                || value.length() > 1024
                || value.startsWith("/")
                || value.endsWith("/")
                || value.contains("\\")
                || value.contains("\0")
                || value.contains(":")) {
            return false;
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static void requireArchivePath(String value) {
        if (value == null
                || value.isBlank()
                || value.length() > 1100
                || value.startsWith("/")
                || value.contains("\\")
                || value.contains("\0")
                || value.contains(":")) {
            throw new IllegalArgumentException("Skill package contains an unsafe ZIP path");
        }
        String path = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Skill package contains an empty ZIP path");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Skill package contains an unsafe ZIP path");
            }
        }
    }

    private static SkillMetadata parseSkillMarkdown(byte[] bytes, String archiveRoot) {
        String text = decodeUtf8(bytes);
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.startsWith("---\n")) {
            throw new IllegalArgumentException("SKILL.md requires YAML frontmatter");
        }
        int closing = normalized.indexOf("\n---\n", 4);
        if (closing < 0) {
            throw new IllegalArgumentException("SKILL.md frontmatter is not closed");
        }
        String yamlSource = normalized.substring(4, closing);
        Object loaded;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setAllowRecursiveKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(10);
            options.setCodePointLimit(MAX_SKILL_MD_BYTES);
            loaded = new Yaml(new SafeConstructor(options)).load(yamlSource);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("SKILL.md frontmatter is invalid YAML", failure);
        }
        if (!(loaded instanceof Map<?, ?> frontmatter)) {
            throw new IllegalArgumentException("SKILL.md frontmatter must be a mapping");
        }
        Set<String> keys = new HashSet<>();
        for (Object key : frontmatter.keySet()) {
            if (!(key instanceof String stringKey)) {
                throw new IllegalArgumentException("SKILL.md frontmatter keys must be strings");
            }
            keys.add(stringKey);
        }
        if (!FRONTMATTER_FIELDS.containsAll(keys)) {
            throw new IllegalArgumentException(
                    "SKILL.md frontmatter contains unsupported fields");
        }
        String name = requiredString(frontmatter, "name", 64);
        if (!SkillPackageSpec.NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Skill name must use lowercase letters, digits, and single hyphens");
        }
        if (!archiveRoot.isEmpty() && !name.equals(archiveRoot)) {
            throw new IllegalArgumentException(
                    "Skill name must match its package directory");
        }
        return new SkillMetadata(
                name,
                requiredString(frontmatter, "description", 1024),
                optionalString(frontmatter, "license", 1024),
                optionalString(frontmatter, "compatibility", 500),
                optionalString(frontmatter, "allowed-tools", 2048),
                metadata(frontmatter.get("metadata")));
    }

    private static Map<String, String> metadata(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> source) || source.size() > 100) {
            throw new IllegalArgumentException("Skill metadata must be a bounded mapping");
        }
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, item) -> {
            if (!(key instanceof String textKey)
                    || textKey.isBlank()
                    || textKey.length() > 256
                    || !(item instanceof String
                            || item instanceof Number
                            || item instanceof Boolean)) {
                throw new IllegalArgumentException(
                        "Skill metadata must contain scalar string entries");
            }
            String textValue = String.valueOf(item);
            if (textValue.length() > 2048) {
                throw new IllegalArgumentException("Skill metadata value exceeds its limit");
            }
            result.put(textKey, textValue);
        });
        return Map.copyOf(result);
    }

    private static String requiredString(Map<?, ?> source, String key, int maximumLength) {
        Object value = source.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("SKILL.md " + key + " must be a string");
        }
        String normalized = text.strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    "SKILL.md " + key + " is blank or exceeds its limit");
        }
        return normalized;
    }

    private static String optionalString(Map<?, ?> source, String key, int maximumLength) {
        if (!source.containsKey(key)) {
            return "";
        }
        Object value = source.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("SKILL.md " + key + " must be a string");
        }
        String normalized = text.strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    "SKILL.md " + key + " is blank or exceeds its limit");
        }
        return normalized;
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException("SKILL.md must be valid UTF-8", failure);
        }
    }

    private static long readEntry(
            InputStream input,
            ByteArrayOutputStream capture,
            long totalRemaining,
            long entryLimit) throws IOException {
        if (totalRemaining < 0) {
            throw new IllegalArgumentException("Skill package exceeds 50 MiB unpacked");
        }
        byte[] buffer = new byte[8192];
        long copied = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            copied += read;
            if (copied > entryLimit || copied > totalRemaining) {
                throw new IllegalArgumentException(
                        "Skill package exceeds its unpacked content limit");
            }
            if (capture != null) {
                capture.write(buffer, 0, read);
            }
        }
        return copied;
    }

    private static long copyBounded(InputStream input, OutputStream output, long limit)
            throws IOException {
        byte[] buffer = new byte[8192];
        long copied = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            copied += read;
            if (copied > limit) {
                throw new IllegalArgumentException("Skill package exceeds 20 MiB");
            }
            output.write(buffer, 0, read);
        }
        return copied;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void delete(Path staged) {
        if (staged == null) {
            return;
        }
        try {
            Files.deleteIfExists(staged);
        } catch (IOException cleanupFailure) {
            staged.toFile().deleteOnExit();
        }
    }

    record SkillMetadata(
            String name,
            String description,
            String license,
            String compatibility,
            String allowedTools,
            Map<String, String> metadata) {
    }

    record InspectedPackage(
            SkillMetadata metadata, List<SkillPackageSpec.FileEntry> files) {
    }

    record StagedSkillPackage(
            Path path,
            long contentLength,
            String sha256,
            SkillMetadata metadata,
            List<SkillPackageSpec.FileEntry> files)
            implements AutoCloseable {

        InputStream open() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void close() {
            delete(path);
        }
    }
}
