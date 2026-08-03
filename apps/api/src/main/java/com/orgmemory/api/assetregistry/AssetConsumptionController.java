package com.orgmemory.api.assetregistry;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.AssetCatalogSort;
import com.orgmemory.core.assetregistry.AssetRecommendationPage;
import com.orgmemory.core.assetregistry.AssetRegistryService;
import com.orgmemory.core.assetregistry.AssetView;
import com.orgmemory.core.assetregistry.CapabilityPackDefinition;
import com.orgmemory.core.assetregistry.CapabilityPackService;
import com.orgmemory.core.assetregistry.PackJourney;
import com.orgmemory.core.assetregistry.prompt.PromptEvaluationComparison;
import com.orgmemory.core.assetregistry.prompt.PromptEvaluationResult;
import com.orgmemory.core.assetregistry.prompt.PromptExecutionService;
import com.orgmemory.core.assetregistry.promptcontract.PromptRenderResult;
import com.orgmemory.core.assetregistry.promptcontract.PromptRunResult;
import com.orgmemory.core.assetregistry.skill.SkillDistributionOperations;
import com.orgmemory.core.assetregistry.skill.SkillInstallManifest;
import com.orgmemory.core.assetregistry.WorkInstructionService;
import com.orgmemory.core.assetregistry.WorkInstructionView;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
class AssetConsumptionController {

    private final CurrentActorProvider actors;
    private final AssetRegistryService assets;
    private final PromptExecutionService prompts;
    private final WorkInstructionService instructions;
    private final CapabilityPackService packs;
    private final SkillDistributionOperations skills;

    AssetConsumptionController(
            CurrentActorProvider actors,
            AssetRegistryService assets,
            PromptExecutionService prompts,
            WorkInstructionService instructions,
            CapabilityPackService packs,
            SkillDistributionOperations skills) {
        this.actors = actors;
        this.assets = assets;
        this.prompts = prompts;
        this.instructions = instructions;
        this.packs = packs;
        this.skills = skills;
    }

    record PromptVariablesRequest(Map<String, Object> variables) {
    }

    record PromptRunRequest(
            Map<String, Object> variables,
            String knowledgeQuery,
            String requestId) {
    }

    record PromptComparisonRequest(
            UUID baselineReleaseId,
            UUID candidateReleaseId) {
    }

    record ForkReleaseRequest(
            String namespace,
            String slug,
            UUID knowledgeSpaceId) {
    }

    record PackProgressRequest(boolean completed) {
    }

    @PostMapping("/{assetId}/releases/{releaseId}/prompt/render")
    @Operation(
            operationId = "renderPromptRelease",
            summary = "Deterministically render an exact authorized Prompt release")
    PromptRenderResult renderPrompt(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            @RequestBody PromptVariablesRequest request,
            Authentication authentication) {
        return prompts.render(
                actors.current(authentication),
                assetId,
                releaseId,
                request.variables());
    }

    @PostMapping("/{assetId}/releases/{releaseId}/prompt/runs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "runPromptRelease",
            summary = "Run an exact Prompt release through the provider-neutral AI gateway")
    PromptRunResult runPrompt(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            @RequestBody PromptRunRequest request,
            Authentication authentication) {
        return prompts.run(
                actors.current(authentication),
                assetId,
                releaseId,
                request.variables(),
                request.knowledgeQuery(),
                request.requestId());
    }

    @PostMapping("/{assetId}/releases/{releaseId}/prompt/evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "evaluatePromptRelease",
            summary = "Run the bounded evaluation cases pinned in a Prompt release")
    PromptEvaluationResult evaluatePrompt(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        return prompts.evaluate(
                actors.current(authentication), assetId, releaseId);
    }

    @PostMapping("/{assetId}/prompt/evaluation-comparisons")
    @Operation(
            operationId = "comparePromptReleases",
            summary = "Compare bounded evaluation results for two exact Prompt releases")
    PromptEvaluationComparison comparePrompts(
            @PathVariable UUID assetId,
            @RequestBody PromptComparisonRequest request,
            Authentication authentication) {
        return prompts.compare(
                actors.current(authentication),
                assetId,
                request.baselineReleaseId(),
                request.candidateReleaseId());
    }

    @PostMapping("/{assetId}/releases/{releaseId}/forks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "forkAssetRelease",
            summary = "Fork an exact authorized release into a new mutable draft")
    AssetView forkRelease(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            @RequestBody ForkReleaseRequest request,
            Authentication authentication) {
        return assets.forkRelease(
                actors.current(authentication),
                assetId,
                releaseId,
                request.namespace(),
                request.slug(),
                request.knowledgeSpaceId());
    }

    @GetMapping("/{assetId}/releases/{releaseId}/work-instruction")
    @Operation(
            operationId = "followWorkInstruction",
            summary = "Follow an exact authorized Work Instruction release")
    WorkInstructionView followInstruction(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        return instructions.follow(
                actors.current(authentication), assetId, releaseId);
    }

    @PostMapping("/{assetId}/releases/{releaseId}/work-instruction/acknowledgement")
    @Operation(
            operationId = "acknowledgeWorkInstruction",
            summary = "Idempotently acknowledge an exact Work Instruction release")
    WorkInstructionView acknowledgeInstruction(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        return instructions.acknowledge(
                actors.current(authentication), assetId, releaseId);
    }

    @PostMapping("/{assetId}/releases/{releaseId}/pack-assignment")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "startCapabilityPack",
            summary = "Start or resume an exact authorized Capability Pack release")
    PackJourney startPack(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        return packs.start(
                actors.current(authentication), assetId, releaseId);
    }

    @GetMapping("/{assetId}/releases/{releaseId}/pack-journey")
    @Operation(
            operationId = "getCapabilityPackJourney",
            summary = "Read an actor-scoped Capability Pack journey")
    PackJourney getPack(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        return packs.get(
                actors.current(authentication), assetId, releaseId);
    }

    @GetMapping("/{assetId}/releases/{releaseId}/pack-definition")
    @Operation(
            operationId = "getCapabilityPackDefinition",
            summary = "Read the ordered authorized items pinned by a Capability Pack release")
    CapabilityPackDefinition getPackDefinition(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        return packs.describe(
                actors.current(authentication), assetId, releaseId);
    }

    @GetMapping("/{assetId}/releases/{releaseId}/skill-manifest")
    @Operation(
            operationId = "getSkillInstallContract",
            summary = "Read the browser install contract for one exact authorized Skill release")
    ResponseEntity<SkillInstallManifest> getSkillInstallContract(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken)) {
            throw new AccessDeniedException("Browser session required");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(skills.manifest(
                        actors.current(authentication),
                        assetId,
                        releaseId));
    }

    @PutMapping("/{assetId}/releases/{releaseId}/pack-progress/{itemKey}")
    @Operation(
            operationId = "setCapabilityPackProgress",
            summary = "Idempotently update actor-derived progress for one accessible Pack item")
    PackJourney setPackProgress(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            @PathVariable String itemKey,
            @RequestBody PackProgressRequest request,
            Authentication authentication) {
        return packs.setItemCompleted(
                actors.current(authentication),
                assetId,
                releaseId,
                itemKey,
                request.completed());
    }

    @GetMapping("/catalog")
    @Operation(
            operationId = "listAssetCatalog",
            summary = "List exact usable Asset releases authorized for the current actor")
    AssetRecommendationPage catalog(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AssetType type,
            @RequestParam(defaultValue = "RECENTLY_RELEASED")
                    AssetCatalogSort sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "24") int pageSize,
            Authentication authentication) {
        return assets.catalog(
                actors.current(authentication), q, type, sort, page, pageSize);
    }
}
