package com.orgmemory.api.assistant;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assistant.AssistantAssetToolService;
import com.orgmemory.core.assistant.AssistantFeedbackType;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/assistant/tools")
class AssistantAssetToolController {

    private final AssistantAssetToolService tools;
    private final CurrentActorProvider actors;

    AssistantAssetToolController(
            AssistantAssetToolService tools, CurrentActorProvider actors) {
        this.tools = tools;
        this.actors = actors;
    }

    record KnowledgeSearchRequest(String query, String requestId) {
    }

    record PromptRenderRequest(Map<String, Object> variables) {
    }

    record PromptRunRequest(
            Map<String, Object> variables,
            String knowledgeQuery,
            String requestId,
            boolean confirmedExternalProvider) {
    }

    record ConfirmedActionRequest(boolean confirmed) {
    }

    record PackProgressRequest(boolean completed, boolean confirmed) {
    }

    record ForkRequest(
            String namespace,
            String slug,
            UUID knowledgeSpaceId,
            boolean confirmed) {
    }

    record FeedbackRequest(
            AssistantFeedbackType type, String comment, boolean confirmed) {
    }

    @GetMapping("/asset-recommendations")
    @Operation(
            operationId = "recommendAssistantAssets",
            summary = "Recommend exact usable Asset releases without leaking denied candidates")
    AssistantAssetToolService.RecommendationResult recommend(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AssetType type,
            Authentication authentication) {
        return tools.recommend(actors.current(authentication), q, type);
    }

    @PostMapping("/knowledge-search")
    @Operation(
            operationId = "searchAssistantKnowledge",
            summary = "Search canonical permission-aware Knowledge and return citation references")
    AssistantAssetToolService.KnowledgeResult searchKnowledge(
            @RequestBody KnowledgeSearchRequest request,
            Authentication authentication) {
        return tools.searchKnowledge(
                actors.current(authentication), request.query(), request.requestId());
    }

    @GetMapping("/assets/{assetId}/releases/{releaseId}/prompt-form")
    @Operation(
            operationId = "prepareAssistantPrompt",
            summary = "Resolve the variables required by an exact Prompt release")
    AssistantAssetToolService.PromptFormResult preparePrompt(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        return tools.preparePrompt(
                actors.current(authentication), assetId, releaseId);
    }

    @PostMapping("/assets/{assetId}/releases/{releaseId}/prompt-render")
    @Operation(
            operationId = "renderAssistantPrompt",
            summary = "Render an exact Prompt release after variable validation")
    AssistantAssetToolService.PromptRenderToolResult renderPrompt(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            @RequestBody PromptRenderRequest request,
            Authentication authentication) {
        return tools.renderPrompt(
                actors.current(authentication),
                assetId,
                releaseId,
                request.variables());
    }

    @PostMapping("/assets/{assetId}/releases/{releaseId}/prompt-run")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "runAssistantPrompt",
            summary = "Run an exact Prompt release after explicit external-provider confirmation")
    AssistantAssetToolService.PromptRunToolResult runPrompt(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            @RequestBody PromptRunRequest request,
            Authentication authentication) {
        return tools.runPrompt(
                actors.current(authentication),
                assetId,
                releaseId,
                request.variables(),
                request.knowledgeQuery(),
                request.requestId(),
                request.confirmedExternalProvider());
    }

    @GetMapping("/assets/{assetId}/releases/{releaseId}/work-instruction")
    @Operation(
            operationId = "followAssistantWorkInstruction",
            summary = "Guide an exact Work Instruction release")
    AssistantAssetToolService.WorkInstructionToolResult followInstruction(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        return tools.followInstruction(
                actors.current(authentication), assetId, releaseId);
    }

    @PostMapping("/assets/{assetId}/releases/{releaseId}/pack")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "startAssistantPack",
            summary = "Start an exact Pack after explicit state-change confirmation")
    AssistantAssetToolService.PackToolResult startPack(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            @RequestBody ConfirmedActionRequest request,
            Authentication authentication) {
        return tools.startPack(
                actors.current(authentication),
                assetId,
                releaseId,
                request.confirmed());
    }

    @GetMapping("/assets/{assetId}/releases/{releaseId}/pack")
    @Operation(
            operationId = "readAssistantPack",
            summary = "Read an actor-scoped exact Pack journey")
    AssistantAssetToolService.PackToolResult readPack(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        return tools.readPack(
                actors.current(authentication), assetId, releaseId);
    }

    @PutMapping("/assets/{assetId}/releases/{releaseId}/pack/{itemKey}")
    @Operation(
            operationId = "updateAssistantPackProgress",
            summary = "Update actor-derived Pack progress after explicit confirmation")
    AssistantAssetToolService.PackToolResult updatePackProgress(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            @PathVariable String itemKey,
            @RequestBody PackProgressRequest request,
            Authentication authentication) {
        return tools.updatePackProgress(
                actors.current(authentication),
                assetId,
                releaseId,
                itemKey,
                request.completed(),
                request.confirmed());
    }

    @PostMapping("/assets/{assetId}/releases/{releaseId}/fork")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "forkAssistantAssetRelease",
            summary = "Fork an exact release after explicit draft-creation confirmation")
    AssistantAssetToolService.ForkResult fork(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            @RequestBody ForkRequest request,
            Authentication authentication) {
        return tools.fork(
                actors.current(authentication),
                assetId,
                releaseId,
                request.namespace(),
                request.slug(),
                request.knowledgeSpaceId(),
                request.confirmed());
    }

    @PostMapping("/assets/{assetId}/releases/{releaseId}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "submitAssistantAssetFeedback",
            summary = "Submit feedback against an exact release after explicit confirmation")
    AssistantAssetToolService.FeedbackResult feedback(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            @RequestBody FeedbackRequest request,
            Authentication authentication) {
        return tools.feedback(
                actors.current(authentication),
                assetId,
                releaseId,
                request.type(),
                request.comment(),
                request.confirmed());
    }
}
