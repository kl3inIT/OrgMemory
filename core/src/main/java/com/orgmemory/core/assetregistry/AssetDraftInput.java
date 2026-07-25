package com.orgmemory.core.assetregistry;

public record AssetDraftInput(
        String title,
        String summary,
        String classification,
        String schemaVersion,
        String payload) {
}
