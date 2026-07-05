package com.orgmemory.api.capability;

import com.orgmemory.core.capability.AssetVersion;
import java.time.Instant;
import java.util.UUID;

record AssetVersionResponse(
        UUID id,
        UUID assetId,
        int versionNumber,
        String promptTemplate,
        String workflowStepsJson,
        String inputSchemaJson,
        String outputSchemaJson,
        String exampleInput,
        String exampleOutput,
        String changeNote,
        UUID createdByUserId,
        Instant createdAt) {

    static AssetVersionResponse from(AssetVersion version) {
        return new AssetVersionResponse(
                version.getId(),
                version.getAssetId(),
                version.getVersionNumber(),
                version.getPromptTemplate(),
                version.getWorkflowStepsJson(),
                version.getInputSchemaJson(),
                version.getOutputSchemaJson(),
                version.getExampleInput(),
                version.getExampleOutput(),
                version.getChangeNote(),
                version.getCreatedByUserId(),
                version.getCreatedAt());
    }
}
