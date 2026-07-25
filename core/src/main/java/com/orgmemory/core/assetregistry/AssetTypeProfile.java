package com.orgmemory.core.assetregistry;

import java.util.Objects;
import java.util.Set;

public record AssetTypeProfile(AssetType type, Set<String> schemaVersions) {

    public AssetTypeProfile {
        type = Objects.requireNonNull(type, "type");
        schemaVersions = Set.copyOf(Objects.requireNonNull(schemaVersions, "schemaVersions"));
        if (schemaVersions.isEmpty() || schemaVersions.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("An Asset type profile needs supported schema versions");
        }
    }

    public void requireSupported(String schemaVersion) {
        if (!schemaVersions.contains(schemaVersion)) {
            throw new IllegalArgumentException(
                    "Asset type " + type + " does not support schema version " + schemaVersion);
        }
    }
}
