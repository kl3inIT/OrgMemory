package com.orgmemory.core.assetregistry;

import java.util.Set;

public interface AssetPayloadProfile {

    AssetType type();

    Set<String> schemaVersions();

    void validate(String payload);
}
