package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetType;
import java.time.Instant;
import java.util.UUID;

public record AssetConsumptionRelease(
        UUID assetId,
        UUID releaseId,
        UUID revisionId,
        AssetType type,
        String namespace,
        String slug,
        String versionLabel,
        AssetPublicationMode publicationMode,
        String title,
        String summary,
        String classification,
        String schemaVersion,
        String payload,
        String digest,
        AssetAvailability availability,
        Instant releasedAt) {
}
