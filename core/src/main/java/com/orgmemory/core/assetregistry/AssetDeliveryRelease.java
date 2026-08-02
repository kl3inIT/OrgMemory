package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetType;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, consumption-safe Asset data. This projection deliberately excludes
 * drafts, review cases, role assignments, and authoring history.
 */
public record AssetDeliveryRelease(
        UUID assetId,
        UUID releaseId,
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

    static AssetDeliveryRelease from(AssetConsumptionRelease release) {
        return new AssetDeliveryRelease(
                release.assetId(),
                release.releaseId(),
                release.type(),
                release.namespace(),
                release.slug(),
                release.versionLabel(),
                release.publicationMode(),
                release.title(),
                release.summary(),
                release.classification(),
                release.schemaVersion(),
                release.payload(),
                release.digest(),
                release.availability(),
                release.releasedAt());
    }
}
