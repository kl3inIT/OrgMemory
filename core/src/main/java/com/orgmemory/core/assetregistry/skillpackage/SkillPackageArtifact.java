package com.orgmemory.core.assetregistry.skillpackage;

import java.util.Objects;

public record SkillPackageArtifact(
        String sha256,
        long contentLength,
        String mediaType) {

    public static final String ZIP_MEDIA_TYPE = "application/zip";

    public SkillPackageArtifact {
        sha256 = requireSha256(sha256);
        if (contentLength <= 0) {
            throw new IllegalArgumentException(
                    "Skill package content length must be positive");
        }
        mediaType = requireText(mediaType, "mediaType", 128);
    }

    private static String requireSha256(String value) {
        String normalized = requireText(value, "sha256", 64);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "sha256 must be lowercase hexadecimal");
        }
        return normalized;
    }

    private static String requireText(
            String value, String field, int maximumLength) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    field + " is blank or exceeds its limit");
        }
        return normalized;
    }
}
