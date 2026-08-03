package com.orgmemory.core.assetregistry.skill;

import java.util.List;
import java.util.Map;

public record SkillPackageInspection(
        String name,
        String description,
        String license,
        String compatibility,
        String allowedTools,
        Map<String, String> metadata,
        String instructions,
        String sha256,
        long contentLength,
        List<FileEntry> files) {

    public SkillPackageInspection {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public record FileEntry(String path, long size, String sha256) {
    }
}
