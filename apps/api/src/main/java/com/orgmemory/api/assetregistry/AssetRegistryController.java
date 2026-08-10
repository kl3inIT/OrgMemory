package com.orgmemory.api.assetregistry;

import com.orgmemory.api.ApiRequestException;
import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.assetregistry.api.AssetRole;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.AssetDraftInput;
import com.orgmemory.core.assetregistry.AssetGovernanceActions;
import com.orgmemory.core.assetregistry.AssetOwnedSort;
import com.orgmemory.core.assetregistry.AssetRegistryService;
import com.orgmemory.core.assetregistry.AssetReleasedView;
import com.orgmemory.core.assetregistry.AssetReviewDecisionType;
import com.orgmemory.core.assetregistry.AssetSummary;
import com.orgmemory.core.assetregistry.AssetSummaryPage;
import com.orgmemory.core.assetregistry.AssetView;
import com.orgmemory.core.assetregistry.skill.SkillGitHubOperations;
import com.orgmemory.core.assetregistry.skill.SkillGitHubSourcePort;
import com.orgmemory.core.assetregistry.skill.SkillPackageInspection;
import com.orgmemory.core.assetregistry.skill.SkillPackageOperations;
import com.orgmemory.core.assetregistry.skill.SkillActivationOperations;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.shared.error.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    private static final Logger LOG =
            LoggerFactory.getLogger(AssetRegistryController.class);

    enum GenericAssetType {
        PROMPT_TEMPLATE,
        WORK_INSTRUCTION,
        CAPABILITY_PACK;

        AssetType domainType() {
            return AssetType.valueOf(name());
        }
    }

    private final AssetRegistryService assets;
    private final SkillPackageOperations skills;
    private final SkillGitHubOperations skillGitHub;
    private final SkillActivationOperations skillActivations;
    private final CurrentActorProvider actors;

    AssetRegistryController(
            AssetRegistryService assets,
            SkillPackageOperations skills,
            SkillGitHubOperations skillGitHub,
            SkillActivationOperations skillActivations,
            CurrentActorProvider actors) {
        this.assets = assets;
        this.skills = skills;
        this.skillGitHub = skillGitHub;
        this.skillActivations = skillActivations;
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

    record PublishSkillReleaseRequest(String versionLabel) {
    }

    record ShareAssetRequest(
            String principalType, String principalId, AssetRole role) {
    }

    record TransferAssetOwnershipRequest(@NotNull UUID nextOwnerUserId) {
    }

    record SkillActivationRequest(boolean enabled) {
    }

    record SkillActivationView(UUID assetId, boolean enabled) {
    }

    record GitHubSkillSourceRequest(
            @NotBlank @Size(max = 512) String repository,
            String revision,
            String subpath,
            String connectionKey,
            @NotNull UUID knowledgeSpaceId) {

        SkillGitHubOperations.SourceRequest source() {
            return new SkillGitHubOperations.SourceRequest(
                    repository, revision, subpath, connectionKey, knowledgeSpaceId);
        }
    }

    record GitHubSkillImportRequest(
            @Valid @NotNull GitHubSkillSourceRequest source,
            @NotNull @Size(min = 1, max = 20) List<@NotBlank String> paths,
            @NotBlank @Size(max = 128) String namespace,
            KnowledgeClassification classification) {
    }

    record ImportResult(
            String repository,
            String revision,
            SkillGitHubSourcePort.Visibility visibility,
            List<ImportItem> skills) {
    }

    record ImportItem(
            String path,
            boolean imported,
            AssetView asset,
            String errorCode,
            String errorMessage) {
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
            var actor = actors.current(authentication);
            UUID assetId = skills.importPackage(
                    actor,
                    namespace,
                    knowledgeSpaceId,
                    classification,
                    file.getSize(),
                    content);
            return assets.get(actor, assetId);
        } catch (IOException failure) {
            throw new ApiRequestException(
                    "The Skill package could not be read", failure);
        }
    }

    @PostMapping(
            path = "/skills/inspections",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "inspectSkillPackage",
            summary = "Validate and inspect one Agent Skill package without storing it")
    SkillPackageInspection inspectSkill(
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        try (var content = file.getInputStream()) {
            return skills.inspectPackage(
                    actors.current(authentication), file.getSize(), content);
        } catch (IOException failure) {
            throw new ApiRequestException(
                    "The Skill package could not be read", failure);
        }
    }

    @PostMapping("/skills/github/preview")
    @Operation(
            operationId = "previewGitHubSkills",
            summary = "Discover and validate Skills at one GitHub repository revision")
    SkillGitHubOperations.Preview previewGitHubSkills(
            @Valid @RequestBody GitHubSkillSourceRequest request,
            Authentication authentication) {
        return skillGitHub.preview(
                actors.current(authentication), request.source());
    }

    @GetMapping("/skills/github/connections")
    @Operation(
            operationId = "listGitHubSkillConnections",
            summary = "List approved GitHub connections available for private Skill import")
    List<SkillGitHubOperations.ConnectionOption>
            listGitHubSkillConnections(
                    @RequestParam UUID knowledgeSpaceId,
                    Authentication authentication) {
        return skillGitHub.availableConnections(
                actors.current(authentication), knowledgeSpaceId);
    }

    @PostMapping("/skills/github/import")
    @Operation(
            operationId = "importGitHubSkills",
            summary = "Import selected Skills from an exact GitHub commit")
    ImportResult importGitHubSkills(
            @Valid @RequestBody GitHubSkillImportRequest request,
            Authentication authentication) {
        var actor = actors.current(authentication);
        SkillGitHubOperations.ImportResult result = skillGitHub.importSelected(
                actor,
                new SkillGitHubOperations.ImportRequest(
                        request.source().source(),
                        request.paths(),
                        request.namespace(),
                        request.classification() == null
                                ? KnowledgeClassification.INTERNAL
                                : request.classification()));
        return new ImportResult(
                result.repository(),
                result.revision(),
                result.visibility(),
                result.skills().stream()
                        .map(item -> importItem(actor, item))
                        .toList());
    }

    private ImportItem importItem(
            CurrentActor actor,
            SkillGitHubOperations.ImportItem item) {
        if (!item.imported()) {
            return new ImportItem(
                    item.path(), false, null, item.errorCode(), item.errorMessage());
        }
        try {
            return new ImportItem(
                    item.path(), true, assets.get(actor, item.assetId()), "", "");
        } catch (BusinessException failure) {
            LOG.warn(
                    "Imported GitHub Skill is not readable yet path={} assetId={} code={}",
                    item.path(),
                    item.assetId(),
                    failure.code());
            return new ImportItem(
                    item.path(),
                    true,
                    null,
                    failure.code(),
                    failure.getMessage());
        } catch (RuntimeException failure) {
            LOG.warn(
                    "Unexpected GitHub Skill result resolution failure path={} assetId={}",
                    item.path(),
                    item.assetId(),
                    failure);
            return new ImportItem(
                    item.path(),
                    true,
                    null,
                    "skill.github-import-resolution-failed",
                    "The Skill was imported but could not be read yet");
        }
    }

    @PutMapping(
            path = "/{assetId}/skill-draft",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "replaceSkillDraftPackage",
            summary = "Replace the package of one mutable Skill draft")
    AssetView replaceSkillDraft(
            @PathVariable UUID assetId,
            @RequestPart("file") MultipartFile file,
            @RequestParam long expectedLockVersion,
            Authentication authentication) {
        try (var content = file.getInputStream()) {
            var actor = actors.current(authentication);
            UUID replacedId = skills.replacePackage(
                    actor,
                    assetId,
                    expectedLockVersion,
                    file.getSize(),
                    content);
            return assets.get(actor, replacedId);
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

    @GetMapping("/owned")
    @Operation(
            operationId = "listOwnedAssets",
            summary = "List Assets currently owned by the actor")
    AssetSummaryPage owned(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AssetType type,
            @RequestParam(defaultValue = "RECENTLY_UPDATED") AssetOwnedSort sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "24") int pageSize,
            Authentication authentication) {
        return assets.owned(
                actors.current(authentication), q, type, sort, page, pageSize);
    }

    @GetMapping("/{assetId}")
    @Operation(operationId = "getAsset", summary = "Read an Asset's authoring and governance history")
    AssetView get(
            @PathVariable UUID assetId,
            Authentication authentication) {
        return assets.get(actors.current(authentication), assetId);
    }

    @GetMapping("/{assetId}/released")
    @Operation(
            operationId = "getReleasedAsset",
            summary = "Read released Asset content without exposing its working copy or governance history")
    AssetReleasedView getReleased(
            @PathVariable UUID assetId,
            Authentication authentication) {
        return assets.getReleased(actors.current(authentication), assetId);
    }

    @GetMapping("/{assetId}/skill-activation")
    @Operation(
            operationId = "getSkillActivation",
            summary = "Read the current user's opt-in state for one visible Skill")
    SkillActivationView getSkillActivation(
            @PathVariable UUID assetId,
            Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        requireSkill(assets.getReleased(actor, assetId));
        return new SkillActivationView(
                assetId, skillActivations.isEnabled(actor, assetId));
    }

    @PutMapping("/{assetId}/skill-activation")
    @Operation(
            operationId = "setSkillActivation",
            summary = "Enable or disable one visible Skill for the current user")
    SkillActivationView setSkillActivation(
            @PathVariable UUID assetId,
            @RequestBody SkillActivationRequest request,
            Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        requireSkill(assets.getReleased(actor, assetId));
        return new SkillActivationView(
                assetId,
                skillActivations.setEnabled(actor, assetId, request.enabled()));
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

    @PostMapping("/{assetId}/skill-releases")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "publishSkillRelease",
            summary = "Publish a Skill draft as an immutable release")
    AssetView publishSkill(
            @PathVariable UUID assetId,
            @RequestBody PublishSkillReleaseRequest request,
            Authentication authentication) {
        return assets.publishSkillDraft(
                actors.current(authentication),
                assetId,
                request.versionLabel());
    }

    @PostMapping("/{assetId}/direct-releases")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "publishAssetDraft",
            summary = "Publish the current working copy as an immutable Release")
    AssetView publishDraft(
            @PathVariable UUID assetId,
            @RequestBody PublishSkillReleaseRequest request,
            Authentication authentication) {
        return assets.publishDraft(
                actors.current(authentication), assetId, request.versionLabel());
    }

    @PostMapping("/{assetId}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "shareAsset",
            summary = "Share an Asset with one Viewer or Editor audience")
    AssetView share(
            @PathVariable UUID assetId,
            @RequestBody ShareAssetRequest request,
            Authentication authentication) {
        return assets.share(
                actors.current(authentication),
                assetId,
                request.principalType(),
                request.principalId(),
                request.role());
    }

    @DeleteMapping("/{assetId}/shares")
    @Operation(operationId = "unshareAsset", summary = "Remove one Viewer or Editor share")
    AssetView unshare(
            @PathVariable UUID assetId,
            @RequestBody ShareAssetRequest request,
            Authentication authentication) {
        return assets.unshare(
                actors.current(authentication),
                assetId,
                request.principalType(),
                request.principalId(),
                request.role());
    }

    @PostMapping("/{assetId}/ownership-transfer")
    @Operation(operationId = "transferAssetOwnership", summary = "Transfer Asset ownership to another user")
    AssetView transferOwnership(
            @PathVariable UUID assetId,
            @Valid @RequestBody TransferAssetOwnershipRequest request,
            Authentication authentication) {
        return assets.transferOwnership(
                actors.current(authentication), assetId, request.nextOwnerUserId());
    }

    @PostMapping("/{assetId}/ownership-recovery")
    @Operation(
            operationId = "recoverAssetOwnership",
            summary = "Recover an owner-vacant Asset as an administrator")
    AssetView recoverOwnership(
            @PathVariable UUID assetId,
            @Valid @RequestBody TransferAssetOwnershipRequest request,
            Authentication authentication) {
        return assets.recoverOwnership(
                actors.current(authentication), assetId, request.nextOwnerUserId());
    }

    @PostMapping("/{assetId}/withdrawal")
    @Operation(operationId = "withdrawAsset", summary = "Withdraw every Release and retire an owned Asset")
    AssetView withdrawAsset(
            @PathVariable UUID assetId,
            @RequestBody AssetAvailabilityRequest request,
            Authentication authentication) {
        return assets.withdrawAsset(
                actors.current(authentication), assetId, request.reason());
    }

    @PostMapping("/{assetId}/emergency-withdrawal")
    @Operation(
            operationId = "emergencyWithdrawAsset",
            summary = "Emergency-withdraw every Release from an Asset as an administrator")
    AssetView emergencyWithdrawAsset(
            @PathVariable UUID assetId,
            @RequestBody AssetAvailabilityRequest request,
            Authentication authentication) {
        return assets.emergencyWithdrawAsset(
                actors.current(authentication), assetId, request.reason());
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

    private static void requireSkill(AssetReleasedView asset) {
        if (asset.type() != AssetType.SKILL) {
            throw new ApiRequestException("Skill activation is available only for Skill Assets");
        }
    }
}
