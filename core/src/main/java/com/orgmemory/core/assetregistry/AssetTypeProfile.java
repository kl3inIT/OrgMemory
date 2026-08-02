package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetType;
import java.util.Set;

public record AssetTypeProfile(
        AssetType type,
        Set<String> schemaVersions,
        AssetPayloadProfile payloadProfile) {

    public AssetTypeProfile {
        type = java.util.Objects.requireNonNull(type, "type");
        schemaVersions = Set.copyOf(
                java.util.Objects.requireNonNull(schemaVersions, "schemaVersions"));
        payloadProfile = java.util.Objects.requireNonNull(payloadProfile, "payloadProfile");
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

    public void validate(String schemaVersion, String payload) {
        requireSupported(schemaVersion);
        payloadProfile.validate(payload);
    }
}
