package com.orgmemory.api.capability;

import com.orgmemory.core.capability.AssetStatus;
import com.orgmemory.core.capability.AssetType;
import com.orgmemory.core.capability.AssetVisibility;
import com.orgmemory.core.capability.CapabilityAsset;
import com.orgmemory.core.capability.RiskLevel;
import java.time.Instant;
import java.util.UUID;

record CapabilityAssetResponse(
        UUID id,
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
        AssetStatus status,
        AssetVisibility visibility,
        RiskLevel riskLevel,
        UUID currentVersionId,
        UUID createdByUserId,
        long usageCount,
        Instant createdAt,
        Instant updatedAt) {

    static CapabilityAssetResponse from(CapabilityAsset asset, long usageCount) {
        return new CapabilityAssetResponse(
                asset.getId(),
                asset.getOrganizationId(),
                asset.getDepartmentId(),
                asset.getTitle(),
                asset.getSummary(),
                asset.getAssetType(),
                asset.getUseCase(),
                asset.getBusinessProcess(),
                asset.getAiTool(),
                asset.getTagNames(),
                asset.getOwnerUserId(),
                asset.getBackupOwnerUserId(),
                asset.getStatus(),
                asset.getVisibility(),
                asset.getRiskLevel(),
                asset.getCurrentVersionId(),
                asset.getCreatedByUserId(),
                usageCount,
                asset.getCreatedAt(),
                asset.getUpdatedAt());
    }
}
