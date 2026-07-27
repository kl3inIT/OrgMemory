package com.orgmemory.api.assetregistry;

import com.orgmemory.api.ApiRequestException;
import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.assetregistry.AssetDraftInput;
import com.orgmemory.core.assetregistry.AssetGovernanceActions;
import com.orgmemory.core.assetregistry.AssetRegistryService;
import com.orgmemory.core.assetregistry.AssetReviewDecisionType;
import com.orgmemory.core.assetregistry.AssetRole;
import com.orgmemory.core.assetregistry.AssetSummary;
import com.orgmemory.core.assetregistry.AssetType;
import com.orgmemory.core.assetregistry.AssetView;
import com.orgmemory.core.assetregistry.SkillRegistryService;
import com.orgmemory.core.permission.KnowledgeClassification;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/assets")
class AssetRegistryController {

    enum GenericAssetType {
        PROMPT_TEMPLATE,
        WORK_INSTRUCTION,
        CAPABILITY_PACK;

        AssetType domainType() {
            return AssetType.valueOf(name());
        }
    }

    private final AssetRegistryService assets;
    private final SkillRegistryService skills;
    private final CurrentActorProvider actors;

    AssetRegistryController(
            AssetRegistryService assets,
            SkillRegistryService skills,
            CurrentActorProvider actors) {
        this.assets = assets;
        this.skills = skills;
        this.actors = actors;
    }

    record AssetDraftRequest(
            String title,
            String summary,
            String classification,
            String schemaVersion,
            String payload) {

        AssetDraftInput input() {
            return new AssetDraftInput(
                    title, summary, classification, schemaVersion, payload);
        }
    }

    record CreateAssetRequest(
            GenericAssetType type,
            String namespace,
            String slug,
            UUID knowledgeSpaceId,
            AssetDraftRequest draft) {
    }

    record UpdateAssetDraftRequest(
            long expectedLockVersion,
            String title,
            String summary,
            String classification,
            String schemaVersion,
            String payload) {

        AssetDraftInput input() {
            return new AssetDraftInput(
                    title, summary, classification, schemaVersion, payload);
        }
    }

    record SubmitAssetRevisionRequest(String changeNote) {
    }

    record AssetReviewDecisionRequest(
            AssetReviewDecisionType decision, String comment) {
    }

    record PublishAssetReleaseRequest(UUID revisionId, String versionLabel) {
    }

    record AssetAvailabilityRequest(String reason) {
    }

    record AssignAssetRoleRequest(
            String principalType, String principalId, AssetRole role) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createAsset", summary = "Create an Asset and its mutable draft")
    AssetView create(
            @RequestBody CreateAssetRequest request,
            Authentication authentication) {
        if (request.draft() == null) {
            throw new ApiRequestException("An Asset draft is required");
        }
        if (request.type() == null) {
            throw new ApiRequestException("An Asset type is required");
        }
        return assets.create(
                actors.current(authentication),
                request.type().domainType(),
                request.namespace(),
                request.slug(),
                request.knowledgeSpaceId(),
                request.draft().input());
    }

    @PostMapping(path = "/skills", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "importSkillPackage",
            summary = "Validate and import one Agent Skill package")
    AssetView importSkill(
            @RequestPart("file") MultipartFile file,
            @RequestParam String namespace,
            @RequestParam UUID knowledgeSpaceId,
            @RequestParam(defaultValue = "INTERNAL")
                    KnowledgeClassification classification,
            Authentication authentication) {
        try (var content = file.getInputStream()) {
            return skills.importPackage(
                    actors.current(authentication),
                    namespace,
                    knowledgeSpaceId,
                    classification,
                    file.getSize(),
                    content);
        } catch (IOException failure) {
            throw new ApiRequestException(
                    "The Skill package could not be read", failure);
        }
    }

    @GetMapping
    @Operation(operationId = "listAssets", summary = "List released or authoring Assets visible to the actor")
    List<AssetSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AssetType type,
            Authentication authentication) {
        return assets.search(actors.current(authentication), q, type);
    }

    @GetMapping("/{assetId}")
    @Operation(operationId = "getAsset", summary = "Read an authorized Asset and its governance history")
    AssetView get(
            @PathVariable UUID assetId,
            Authentication authentication) {
        return assets.get(actors.current(authentication), assetId);
    }

    @GetMapping("/{assetId}/governance-actions")
    @Operation(
            operationId = "getAssetGovernanceActions",
            summary = "Read the current actor's available Governance actions")
    AssetGovernanceActions governanceActions(
            @PathVariable UUID assetId,
            Authentication authentication) {
        return assets.governanceActions(
                actors.current(authentication), assetId);
    }

    @PutMapping("/{assetId}/draft")
    @Operation(operationId = "updateAssetDraft", summary = "Update a mutable Asset draft")
    AssetView updateDraft(
            @PathVariable UUID assetId,
            @RequestBody UpdateAssetDraftRequest request,
            Authentication authentication) {
        return assets.updateDraft(
                actors.current(authentication),
                assetId,
                request.expectedLockVersion(),
                request.input());
    }

    @PostMapping("/{assetId}/submissions")
    @Operation(operationId = "submitAssetRevision", summary = "Submit an immutable Asset revision for review")
    AssetView submit(
            @PathVariable UUID assetId,
            @RequestBody SubmitAssetRevisionRequest request,
            Authentication authentication) {
        return assets.submit(
                actors.current(authentication), assetId, request.changeNote());
    }

    @PostMapping("/{assetId}/reviews/{reviewCaseId}/decisions")
    @Operation(operationId = "decideAssetReview", summary = "Record a decision against an exact revision digest")
    AssetView decide(
            @PathVariable UUID assetId,
            @PathVariable UUID reviewCaseId,
            @RequestBody AssetReviewDecisionRequest request,
            Authentication authentication) {
        if (request.decision() == null) {
            throw new ApiRequestException("A review decision is required");
        }
        return assets.decide(
                actors.current(authentication),
                assetId,
                reviewCaseId,
                request.decision(),
                request.comment());
    }

    @PostMapping("/{assetId}/releases")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "publishAssetRelease", summary = "Publish an approved immutable Asset revision")
    AssetView publish(
            @PathVariable UUID assetId,
            @RequestBody PublishAssetReleaseRequest request,
            Authentication authentication) {
        return assets.publish(
                actors.current(authentication),
                assetId,
                request.revisionId(),
                request.versionLabel());
    }

    @PostMapping("/{assetId}/releases/{releaseId}/deprecation")
    @Operation(operationId = "deprecateAssetRelease", summary = "Deprecate an Asset release")
    AssetView deprecate(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            @RequestBody AssetAvailabilityRequest request,
            Authentication authentication) {
        return assets.deprecate(
                actors.current(authentication), assetId, releaseId, request.reason());
    }

    @PostMapping("/{assetId}/releases/{releaseId}/withdrawal")
    @Operation(operationId = "withdrawAssetRelease", summary = "Withdraw an Asset release from new use")
    AssetView withdraw(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            @RequestBody AssetAvailabilityRequest request,
            Authentication authentication) {
        return assets.withdraw(
                actors.current(authentication), assetId, releaseId, request.reason());
    }

    @PostMapping("/{assetId}/role-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "assignAssetRole", summary = "Assign an accountable role on an Asset")
    AssetView assignRole(
            @PathVariable UUID assetId,
            @RequestBody AssignAssetRoleRequest request,
            Authentication authentication) {
        return assets.assignRole(
                actors.current(authentication),
                assetId,
                request.principalType(),
                request.principalId(),
                request.role());
    }
}
