package com.orgmemory.core.assetregistry.skillpackage;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record SkillPackageUpload(
        UUID packageId,
        String slug,
        String title,
        String summary,
        String schemaVersion,
        String payload,
        SkillPackageArtifact artifact,
        Map<String, String> storageMetadata,
        InputStream content) {

    public SkillPackageUpload {
        packageId = Objects.requireNonNull(packageId, "packageId");
        slug = Objects.requireNonNull(slug, "slug");
        title = Objects.requireNonNull(title, "title");
        summary = Objects.requireNonNull(summary, "summary");
        schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        payload = Objects.requireNonNull(payload, "payload");
        artifact = Objects.requireNonNull(artifact, "artifact");
        storageMetadata = storageMetadata == null
                ? Map.of()
                : Map.copyOf(storageMetadata);
        content = Objects.requireNonNull(content, "content");
    }
}
