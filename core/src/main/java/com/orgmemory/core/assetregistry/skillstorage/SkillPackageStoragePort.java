package com.orgmemory.core.assetregistry.skillstorage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

public interface SkillPackageStoragePort {

    StoredSkillPackage put(SkillPackageWriteRequest request, InputStream content);

    StoredSkillPackageContent open(String objectKey);

    void delete(String objectKey);

    record SkillPackageWriteRequest(
            UUID organizationId,
            UUID packageId,
            long contentLength,
            String expectedSha256,
            Map<String, String> metadata) {

        public SkillPackageWriteRequest {
            organizationId = java.util.Objects.requireNonNull(
                    organizationId, "organizationId");
            packageId = java.util.Objects.requireNonNull(packageId, "packageId");
            if (contentLength <= 0) {
                throw new IllegalArgumentException(
                        "Skill package content length must be positive");
            }
            expectedSha256 = requireSha256(expectedSha256);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    record StoredSkillPackage(
            String objectKey,
            long contentLength,
            String mediaType,
            String sha256) {

        public StoredSkillPackage {
            objectKey = requireText(objectKey, "objectKey", 1024);
            if (contentLength <= 0) {
                throw new IllegalArgumentException(
                        "Stored Skill package length must be positive");
            }
            mediaType = requireText(mediaType, "mediaType", 128);
            sha256 = requireSha256(sha256);
        }
    }

    record StoredSkillPackageContent(
            InputStream content,
            StoredSkillPackage metadata)
            implements AutoCloseable {

        public StoredSkillPackageContent {
            content = java.util.Objects.requireNonNull(content, "content");
            metadata = java.util.Objects.requireNonNull(metadata, "metadata");
        }

        @Override
        public void close() throws IOException {
            content.close();
        }
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
        String normalized = java.util.Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    field + " is blank or exceeds its limit");
        }
        return normalized;
    }
}
