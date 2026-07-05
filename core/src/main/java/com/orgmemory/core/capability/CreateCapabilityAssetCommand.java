package com.orgmemory.core.capability;

import java.util.UUID;

public record CreateCapabilityAssetCommand(
        UUID organizationId,
        UUID departmentId,
        String title,
        String summary,
        AssetType assetType,
        String useCase,
        String businessProcess,
        String aiTool,
        String tagNames,
        UUID ownerUserId,
        UUID backupOwnerUserId,
        UUID createdByUserId,
        AssetVisibility visibility,
        RiskLevel riskLevel,
        String promptTemplate,
        String workflowStepsJson,
        String inputSchemaJson,
        String outputSchemaJson,
        String exampleInput,
        String exampleOutput) {
}
