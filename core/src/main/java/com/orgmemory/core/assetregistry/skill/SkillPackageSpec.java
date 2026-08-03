package com.orgmemory.core.assetregistry.skill;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

record SkillPackageSpec(
        String name,
        String description,
        String license,
        String compatibility,
        String allowedTools,
        Map<String, String> metadata,
        Origin origin,
        Artifact artifact,
        List<FileEntry> files) {

    static final Pattern NAME =
            Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Set<String> MEDIA_TYPES =
            Set.of("application/zip", "application/octet-stream");

    public SkillPackageSpec {
        name = required(name, "name", 64);
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Skill name must use lowercase letters, digits, and single hyphens");
        }
        description = required(description, "description", 1024);
        license = optional(license, 1024);
        compatibility = optional(compatibility, 500);
        allowedTools = optional(allowedTools, 2048);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (metadata.size() > 100
                || metadata.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null
                                || entry.getValue() == null
                                || entry.getKey().isBlank()
                                || entry.getKey().length() > 256
                                || entry.getValue().length() > 2048)) {
            throw new IllegalArgumentException("Skill metadata exceeds its bounded string map");
        }
        artifact = Objects.requireNonNull(artifact, "artifact");
        files = files == null ? List.of() : List.copyOf(files);
        if (files.isEmpty() || files.size() > 300) {
            throw new IllegalArgumentException("Skill package needs between 1 and 300 files");
        }
        if (files.stream().noneMatch(file -> file.path().equals("SKILL.md"))) {
            throw new IllegalArgumentException("Skill package manifest must include SKILL.md");
        }
    }

    public record Origin(
            String repository,
            String revision,
            String path,
            SkillGitHubSourcePort.Visibility visibility) {

        public Origin {
            repository = required(repository, "origin.repository", 256);
            if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
                throw new IllegalArgumentException("Skill origin repository is invalid");
            }
            revision = required(revision, "origin.revision", 40);
            if (!revision.matches("[0-9a-f]{40}")) {
                throw new IllegalArgumentException(
                        "Skill origin revision must be a full Git commit SHA");
            }
            path = required(path, "origin.path", 1024);
            if (!SkillPackageInspector.isSafeRelativePath(path)) {
                throw new IllegalArgumentException("Skill origin path is unsafe");
            }
            visibility = Objects.requireNonNull(visibility, "origin.visibility");
        }
    }

    public record Artifact(
            String sha256,
            long contentLength,
            String mediaType) {

        public Artifact {
            sha256 = required(sha256, "artifact.sha256", 64);
            if (!sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Skill artifact digest must be lowercase SHA-256");
            }
            if (contentLength <= 0 || contentLength > SkillPackageInspector.MAX_ARCHIVE_BYTES) {
                throw new IllegalArgumentException("Skill artifact size exceeds its limit");
            }
            mediaType = required(mediaType, "artifact.mediaType", 128);
            if (!MEDIA_TYPES.contains(mediaType)) {
                throw new IllegalArgumentException("Unsupported Skill artifact media type");
            }
        }
    }

    public record FileEntry(String path, long size, String sha256) {

        public FileEntry {
            path = required(path, "file.path", 1024);
            if (!SkillPackageInspector.isSafeRelativePath(path)) {
                throw new IllegalArgumentException("Skill manifest contains an unsafe path");
            }
            if (size < 0 || size > SkillPackageInspector.MAX_UNPACKED_BYTES) {
                throw new IllegalArgumentException("Skill manifest entry size exceeds its limit");
            }
            sha256 = required(sha256, "file.sha256", 64);
            if (!sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Skill file digest must be lowercase SHA-256");
            }
        }
    }

    private static String optional(String value, int maximumLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.strip();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException("Skill metadata value exceeds its limit");
        }
        return normalized;
    }

    private static String required(String value, String field, int maximumLength) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is blank or exceeds its limit");
        }
        return normalized;
    }
}
