package com.orgmemory.api.assetregistry;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.assetregistry.AssetDeliveryRelease;
import com.orgmemory.core.assetregistry.AssetDeliveryService;
import com.orgmemory.core.assetregistry.AssetRecommendation;
import com.orgmemory.core.assetregistry.AssetRelationResolution;
import com.orgmemory.core.assetregistry.AssetType;
import com.orgmemory.core.assetregistry.CapabilityPackDefinition;
import com.orgmemory.core.assetregistry.PromptExecutionService;
import com.orgmemory.core.assetregistry.PromptRenderResult;
import com.orgmemory.core.assetregistry.SkillDistributionService;
import com.orgmemory.core.assetregistry.SkillInstallManifest;
import io.swagger.v3.oas.annotations.Operation;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Stable delivery API for external read-only adapters. It never exposes mutable
 * drafts, governance records, or direct persistence contracts.
 */
@RestController
@RequestMapping("/api/asset-delivery")
class AssetDeliveryController {

    private static final String ASSET_READ_AUTHORITY =
            "SCOPE_assets:read";

    private final AssetDeliveryService delivery;
    private final PromptExecutionService prompts;
    private final SkillDistributionService skills;
    private final CurrentActorProvider actors;

    AssetDeliveryController(
            AssetDeliveryService delivery,
            PromptExecutionService prompts,
            SkillDistributionService skills,
            CurrentActorProvider actors) {
        this.delivery = delivery;
        this.prompts = prompts;
        this.skills = skills;
        this.actors = actors;
    }

    record PromptVariablesRequest(Map<String, Object> variables) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + ASSET_READ_AUTHORITY + "')")
    @Operation(
            operationId = "searchReleasedAssets",
            summary = "Search exact released Assets authorized for the current actor")
    List<AssetRecommendation> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AssetType type,
            Authentication authentication) {
        return delivery.search(actors.current(authentication), q, type);
    }

    @GetMapping("/{assetId}")
    @PreAuthorize("hasAuthority('" + ASSET_READ_AUTHORITY + "')")
    @Operation(
            operationId = "getLatestReleasedAsset",
            summary = "Read the latest usable immutable release for an Asset")
    AssetDeliveryRelease get(
            @PathVariable UUID assetId,
            Authentication authentication) {
        return delivery.get(actors.current(authentication), assetId);
    }

    @GetMapping("/{assetId}/releases/{releaseId}")
    @PreAuthorize("hasAuthority('" + ASSET_READ_AUTHORITY + "')")
    @Operation(
            operationId = "getReleasedAsset",
            summary = "Read one exact usable immutable Asset release")
    AssetDeliveryRelease getRelease(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        return delivery.getRelease(
                actors.current(authentication), assetId, releaseId);
    }

    @GetMapping("/{assetId}/releases/{releaseId}/relations")
    @PreAuthorize("hasAuthority('" + ASSET_READ_AUTHORITY + "')")
    @Operation(
            operationId = "resolveReleasedAssetRelations",
            summary = "Resolve only independently authorized relations of an exact release")
    AssetRelationResolution relations(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        return delivery.resolveRelations(
                actors.current(authentication), assetId, releaseId);
    }

    @GetMapping("/{assetId}/releases/{releaseId}/skill-manifest")
    @PreAuthorize("hasAuthority('" + ASSET_READ_AUTHORITY + "')")
    @Operation(
            operationId = "getReleasedSkillManifest",
            summary = "Read the install manifest for one exact usable Skill release")
    ResponseEntity<SkillInstallManifest> skillManifest(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(skills.manifest(
                        actors.current(authentication),
                        assetId,
                        releaseId));
    }

    @GetMapping("/skills/{namespace}/{slug}/versions/{version}/manifest")
    @PreAuthorize("hasAuthority('" + ASSET_READ_AUTHORITY + "')")
    @Operation(
            operationId = "resolveReleasedSkillManifest",
            summary = "Resolve an exact usable Skill release by coordinate and version")
    ResponseEntity<SkillInstallManifest> resolveSkillManifest(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(skills.manifest(
                        actors.current(authentication),
                        namespace,
                        slug,
                        version));
    }

    @GetMapping("/{assetId}/releases/{releaseId}/skill-package")
    @PreAuthorize("hasAuthority('" + ASSET_READ_AUTHORITY + "')")
    @Operation(
            operationId = "downloadReleasedSkillPackage",
            summary = "Stream the verified package for one exact usable Skill release")
    ResponseEntity<StreamingResponseBody> skillPackage(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        var content = skills.open(
                actors.current(authentication),
                assetId,
                releaseId);
        StreamingResponseBody body = output -> {
            try (content) {
                content.stream().transferTo(output);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        content.manifest().mediaType()))
                .contentLength(content.manifest().packageLength())
                .cacheControl(CacheControl.noStore())
                .eTag(content.manifest().packageDigest())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        content.fileName(),
                                        StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    @GetMapping("/{assetId}/releases/{releaseId}/pack")
    @PreAuthorize("hasAuthority('" + ASSET_READ_AUTHORITY + "')")
    @Operation(
            operationId = "getReleasedCapabilityPack",
            summary = "Read a Pack definition with independently authorized pinned items")
    CapabilityPackDefinition pack(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        return delivery.getPack(
                actors.current(authentication), assetId, releaseId);
    }

    @PostMapping("/{assetId}/releases/{releaseId}/prompt-render")
    @PreAuthorize("hasAuthority('" + ASSET_READ_AUTHORITY + "')")
    @Operation(
            operationId = "renderReleasedPrompt",
            summary = "Deterministically render an exact authorized Prompt release")
    PromptRenderResult renderPrompt(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            @RequestBody PromptVariablesRequest request,
            Authentication authentication) {
        var actor = actors.current(authentication);
        PromptRenderResult result = prompts.render(
                actor,
                assetId,
                releaseId,
                request.variables());
        delivery.auditPromptRender(actor, assetId, releaseId);
        return result;
    }
}
