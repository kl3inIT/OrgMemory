package com.orgmemory.core.assetregistry;

import java.util.List;
import java.util.UUID;

public record AssetRelationResolution(
        UUID assetId,
        UUID releaseId,
        boolean accessGap,
        List<Relation> relations) {

    public AssetRelationResolution {
        relations = List.copyOf(relations);
    }

    public record Relation(
            String key,
            String kind,
            UUID resourceId,
            UUID pinnedVersionId,
            String title,
            String versionLabel,
            boolean required) {
    }
}
