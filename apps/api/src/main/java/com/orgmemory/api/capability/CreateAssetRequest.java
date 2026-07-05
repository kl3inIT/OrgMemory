package com.orgmemory.api.capability;

import com.orgmemory.core.capability.AssetVisibility;
import com.orgmemory.core.capability.AssetType;
import com.orgmemory.core.capability.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record CreateAssetRequest(
        @NotNull UUID organizationId,
        UUID departmentId,
        @NotBlank String title,
        @NotBlank String summary,
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
