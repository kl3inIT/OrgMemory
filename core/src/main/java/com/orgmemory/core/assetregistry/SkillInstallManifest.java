package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.consumption.AssetPublicationMode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public, immutable installation contract for one exact Skill release.
 *
 * <p>The object-storage reference is deliberately absent. Consumers receive
 * only content identity and the inspected file manifest.
 */
public record SkillInstallManifest(
        UUID assetId,
        UUID releaseId,
        String namespace,
        String slug,
        String coordinate,
        String version,
        AssetPublicationMode publicationMode,
        String title,
        String description,
        String releaseDigest,
        String packageDigest,
        long packageLength,
        String mediaType,
        String license,
        String compatibility,
        String allowedTools,
        Map<String, String> metadata,
        List<File> files) {

    public SkillInstallManifest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public record File(String path, long size, String sha256) {
    }
}
