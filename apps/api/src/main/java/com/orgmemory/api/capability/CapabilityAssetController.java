package com.orgmemory.api.capability;

import com.orgmemory.core.capability.AssetStatus;
import com.orgmemory.core.capability.AssetType;
import com.orgmemory.core.capability.AssetVisibility;
import com.orgmemory.core.capability.CapabilityAsset;
import com.orgmemory.core.capability.CapabilityAssetService;
import com.orgmemory.core.capability.CreateCapabilityAssetCommand;
import com.orgmemory.core.capability.RiskLevel;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
class CapabilityAssetController {

    private final CapabilityAssetService service;

    CapabilityAssetController(CapabilityAssetService service) {
        this.service = service;
    }

    @GetMapping
    List<CapabilityAssetResponse> search(@RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) AssetType assetType,
            @RequestParam(required = false) String q) {
        return service.search(status, assetType, q).stream()
                .map(asset -> CapabilityAssetResponse.from(asset, service.usageCount(asset.getId())))
                .toList();
    }

    @PostMapping
    CapabilityAssetResponse create(@Valid @RequestBody CreateAssetRequest request) {
        CapabilityAsset asset = service.create(new CreateCapabilityAssetCommand(
                request.organizationId(),
                request.departmentId(),
                request.title(),
                request.summary(),
                request.assetType() == null ? AssetType.WORKFLOW_AUTOMATION : request.assetType(),
                request.useCase(),
                request.businessProcess(),
                request.aiTool(),
                request.tagNames(),
                request.ownerUserId(),
                request.backupOwnerUserId(),
                request.createdByUserId(),
                request.visibility() == null ? AssetVisibility.TEAM : request.visibility(),
                request.riskLevel() == null ? RiskLevel.LOW : request.riskLevel(),
                request.promptTemplate(),
                request.workflowStepsJson(),
                request.inputSchemaJson(),
                request.outputSchemaJson(),
                request.exampleInput(),
                request.exampleOutput()));
        return CapabilityAssetResponse.from(asset, service.usageCount(asset.getId()));
    }

    @GetMapping("/{assetId}")
    CapabilityAssetResponse get(@PathVariable UUID assetId) {
        CapabilityAsset asset = service.get(assetId);
        return CapabilityAssetResponse.from(asset, service.usageCount(assetId));
    }

    @GetMapping("/{assetId}/versions")
    List<AssetVersionResponse> versions(@PathVariable UUID assetId) {
        return service.versions(assetId).stream().map(AssetVersionResponse::from).toList();
    }

    @PatchMapping("/{assetId}/submit-review")
    CapabilityAssetResponse submitForReview(@PathVariable UUID assetId, @RequestBody(required = false) ReviewRequest request) {
        ReviewRequest body = request == null ? new ReviewRequest(null, null) : request;
        CapabilityAsset asset = service.submitForReview(assetId, body.reviewerUserId(), body.comment());
        return CapabilityAssetResponse.from(asset, service.usageCount(assetId));
    }

    @PatchMapping("/{assetId}/approve")
    CapabilityAssetResponse approve(@PathVariable UUID assetId, @RequestBody(required = false) ReviewRequest request) {
        ReviewRequest body = request == null ? new ReviewRequest(null, null) : request;
        CapabilityAsset asset = service.approve(assetId, body.reviewerUserId(), body.comment());
        return CapabilityAssetResponse.from(asset, service.usageCount(assetId));
    }

    @PatchMapping("/{assetId}/reject")
    CapabilityAssetResponse reject(@PathVariable UUID assetId, @RequestBody(required = false) ReviewRequest request) {
        ReviewRequest body = request == null ? new ReviewRequest(null, null) : request;
        CapabilityAsset asset = service.reject(assetId, body.reviewerUserId(), body.comment());
        return CapabilityAssetResponse.from(asset, service.usageCount(assetId));
    }

    @PatchMapping("/{assetId}/deprecate")
    CapabilityAssetResponse deprecate(@PathVariable UUID assetId, @RequestBody(required = false) ReviewRequest request) {
        ReviewRequest body = request == null ? new ReviewRequest(null, null) : request;
        CapabilityAsset asset = service.deprecate(assetId, body.reviewerUserId(), body.comment());
        return CapabilityAssetResponse.from(asset, service.usageCount(assetId));
    }

    @PatchMapping("/{assetId}/backup-owner")
    CapabilityAssetResponse assignBackupOwner(@PathVariable UUID assetId, @Valid @RequestBody BackupOwnerRequest request) {
        CapabilityAsset asset = service.assignBackupOwner(assetId, request.backupOwnerUserId(), null,
                "Assigned backup owner through OrgMemory web");
        return CapabilityAssetResponse.from(asset, service.usageCount(assetId));
    }

    @PostMapping("/{assetId}/usage")
    UsageResponse recordUsage(@PathVariable UUID assetId, @Valid @RequestBody UsageRequest request) {
        long usageCount = service.recordUsage(assetId, request.userId(), request.eventType(), request.metadataJson());
        return new UsageResponse(assetId, usageCount);
    }
}
