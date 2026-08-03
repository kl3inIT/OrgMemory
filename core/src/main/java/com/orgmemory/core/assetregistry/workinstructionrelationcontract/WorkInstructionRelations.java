package com.orgmemory.core.assetregistry.workinstructionrelationcontract;

import java.util.List;
import java.util.UUID;

public record WorkInstructionRelations(
        boolean accessGap,
        List<Relation> relations) {

    public WorkInstructionRelations {
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
