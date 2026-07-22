package com.orgmemory.api.capability;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.capability.AssetStatus;
import com.orgmemory.core.capability.AssetType;
import com.orgmemory.core.capability.AssetVisibility;
import com.orgmemory.core.capability.CapabilityAsset;
import com.orgmemory.core.capability.CapabilityAssetService;
import com.orgmemory.core.capability.CreateCapabilityAssetCommand;
import com.orgmemory.core.capability.RiskLevel;
import com.orgmemory.core.organization.CurrentActor;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
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
    private final CurrentActorProvider actors;

    CapabilityAssetController(CapabilityAssetService service, CurrentActorProvider actors) {
        this.service = service;
        this.actors = actors;
    }

    @GetMapping
    List<CapabilityAssetResponse> search(@RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) AssetType assetType,
            @RequestParam(required = false) String q,
            Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        return service.searchListings(actor, status, assetType, q).stream()
                .map(listing -> CapabilityAssetResponse.from(listing.asset(), listing.usageCount()))
                .toList();
    }

    @PostMapping
    CapabilityAssetResponse create(@Valid @RequestBody CreateAssetRequest request, Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        CapabilityAsset asset = service.create(actor, new CreateCapabilityAssetCommand(
                actor.organizationId(),
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
                actor.userId(),
                request.visibility() == null ? AssetVisibility.TEAM : request.visibility(),
                request.riskLevel() == null ? RiskLevel.LOW : request.riskLevel(),
                request.promptTemplate(),
                request.workflowStepsJson(),
                request.inputSchemaJson(),
                request.outputSchemaJson(),
                request.exampleInput(),
                request.exampleOutput()));
        return CapabilityAssetResponse.from(asset, service.usageCount(actor, asset.getId()));
    }

    @GetMapping("/{assetId}")
    CapabilityAssetResponse get(@PathVariable UUID assetId, Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        CapabilityAsset asset = service.get(actor, assetId);
        return CapabilityAssetResponse.from(asset, service.usageCount(actor, assetId));
    }

    @GetMapping("/{assetId}/versions")
    List<AssetVersionResponse> versions(@PathVariable UUID assetId, Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        return service.versions(actor, assetId).stream().map(AssetVersionResponse::from).toList();
    }

    @PatchMapping("/{assetId}/submit-review")
    CapabilityAssetResponse submitForReview(@PathVariable UUID assetId,
            @RequestBody(required = false) ReviewRequest request, Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        ReviewRequest body = request == null ? new ReviewRequest(null, null) : request;
        CapabilityAsset asset = service.submitForReview(actor, assetId, body.comment());
        return CapabilityAssetResponse.from(asset, service.usageCount(actor, assetId));
    }

    @PatchMapping("/{assetId}/approve")
    CapabilityAssetResponse approve(@PathVariable UUID assetId,
            @RequestBody(required = false) ReviewRequest request, Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        ReviewRequest body = request == null ? new ReviewRequest(null, null) : request;
        CapabilityAsset asset = service.approve(actor, assetId, body.comment());
        return CapabilityAssetResponse.from(asset, service.usageCount(actor, assetId));
    }

    @PatchMapping("/{assetId}/reject")
    CapabilityAssetResponse reject(@PathVariable UUID assetId,
            @RequestBody(required = false) ReviewRequest request, Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        ReviewRequest body = request == null ? new ReviewRequest(null, null) : request;
        CapabilityAsset asset = service.reject(actor, assetId, body.comment());
        return CapabilityAssetResponse.from(asset, service.usageCount(actor, assetId));
    }

    @PatchMapping("/{assetId}/deprecate")
    CapabilityAssetResponse deprecate(@PathVariable UUID assetId,
            @RequestBody(required = false) ReviewRequest request, Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        ReviewRequest body = request == null ? new ReviewRequest(null, null) : request;
        CapabilityAsset asset = service.deprecate(actor, assetId, body.comment());
        return CapabilityAssetResponse.from(asset, service.usageCount(actor, assetId));
    }

    @PatchMapping("/{assetId}/backup-owner")
    CapabilityAssetResponse assignBackupOwner(@PathVariable UUID assetId,
            @Valid @RequestBody BackupOwnerRequest request, Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        CapabilityAsset asset = service.assignBackupOwner(actor, assetId, request.backupOwnerUserId(),
                "Assigned backup owner through OrgMemory web");
        return CapabilityAssetResponse.from(asset, service.usageCount(actor, assetId));
    }

    @PostMapping("/{assetId}/usage")
    UsageResponse recordUsage(@PathVariable UUID assetId,
            @Valid @RequestBody UsageRequest request, Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        long usageCount = service.recordUsage(actor, assetId, request.eventType(), request.metadataJson());
        return new UsageResponse(assetId, usageCount);
    }
}
