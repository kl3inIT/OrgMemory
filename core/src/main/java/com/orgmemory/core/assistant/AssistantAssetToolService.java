package com.orgmemory.core.assistant;

import com.orgmemory.core.assetregistry.AssetConsumptionRelease;
import com.orgmemory.core.assetregistry.AssetRecommendation;
import com.orgmemory.core.assetregistry.AssetRegistryService;
import com.orgmemory.core.assetregistry.AssetType;
import com.orgmemory.core.assetregistry.AssetView;
import com.orgmemory.core.assetregistry.CapabilityPackService;
import com.orgmemory.core.assetregistry.PackJourney;
import com.orgmemory.core.assetregistry.PromptExecutionService;
import com.orgmemory.core.assetregistry.PromptRenderResult;
import com.orgmemory.core.assetregistry.PromptRunResult;
import com.orgmemory.core.assetregistry.PromptTemplateRenderer;
import com.orgmemory.core.assetregistry.PromptTemplateSpec;
import com.orgmemory.core.assetregistry.WorkInstructionService;
import com.orgmemory.core.assetregistry.WorkInstructionView;
import com.orgmemory.core.knowledge.search.PermissionAwareKnowledgeSearch;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Closed-world in-app Assistant tools. The public surface deliberately has no
 * approval, publication, withdrawal, role, or arbitrary execution operation.
 */
public class AssistantAssetToolService {

    private final AssetRegistryService assets;
    private final PromptExecutionService prompts;
    private final PromptTemplateRenderer renderer;
    private final WorkInstructionService instructions;
    private final CapabilityPackService packs;
    private final PermissionAwareKnowledgeSearch knowledge;
    private final AssistantAssetTraceRecorder traces;

    public AssistantAssetToolService(
            AssetRegistryService assets,
            PromptExecutionService prompts,
            PromptTemplateRenderer renderer,
            WorkInstructionService instructions,
            CapabilityPackService packs,
            PermissionAwareKnowledgeSearch knowledge,
            AssistantAssetTraceRecorder traces) {
        this.assets = assets;
        this.prompts = prompts;
        this.renderer = renderer;
        this.instructions = instructions;
        this.packs = packs;
        this.knowledge = knowledge;
        this.traces = traces;
    }

    public RecommendationResult recommend(
            CurrentActor actor, String query, AssetType type) {
        List<AssetRecommendation> recommendations =
                assets.recommend(actor, query, type);
        List<AssistantReleaseRef> refs = recommendations.stream()
                .map(item -> new AssistantReleaseRef(
                        item.assetId(), item.releaseId(), item.releaseDigest()))
                .toList();
        UUID traceId = traces.record(
                actor,
                AssistantAssetAction.RECOMMEND_ASSETS,
                refs,
                List.of(),
                Map.of(),
                Map.of("queryShape", shape(query), "type", type == null ? "ANY" : type.name()),
                Map.of("resultCount", recommendations.size()));
        return new RecommendationResult(traceId, recommendations);
    }

    public KnowledgeResult searchKnowledge(
            CurrentActor actor, String query, String requestId) {
        var result = knowledge.search(actor, query, 5, requestId);
        List<KnowledgeCitation> citations = result.evidence().stream()
                .map(evidence -> new KnowledgeCitation(
                        evidence.chunkId(),
                        evidence.knowledgeAssetId(),
                        evidence.sourceRevisionId(),
                        evidence.title(),
                        evidence.startPage(),
                        evidence.endPage(),
                        evidence.heading()))
                .toList();
        List<Map<String, Object>> refs = result.evidence().stream()
                .map(evidence -> {
                    Map<String, Object> ref = new java.util.LinkedHashMap<>();
                    ref.put("chunkId", evidence.chunkId());
                    ref.put("knowledgeAssetId", evidence.knowledgeAssetId());
                    if (evidence.sourceRevisionId() != null) {
                        ref.put("sourceRevisionId", evidence.sourceRevisionId());
                    }
                    return ref;
                })
                .toList();
        UUID traceId = traces.record(
                actor,
                AssistantAssetAction.SEARCH_KNOWLEDGE,
                List.of(),
                refs,
                Map.of(),
                Map.of("queryShape", shape(query)),
                Map.of("citationCount", citations.size()));
        return new KnowledgeResult(traceId, result.requestId(), citations);
    }

    public PromptFormResult preparePrompt(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        AssetConsumptionRelease release = assets.releaseForUse(
                actor, assetId, releaseId, AssetType.PROMPT_TEMPLATE);
        PromptTemplateSpec spec = renderer.parse(release.payload());
        UUID traceId = record(
                actor,
                AssistantAssetAction.PREPARE_PROMPT,
                release,
                Map.of(),
                Map.of("variableCount", spec.variables().size()));
        return new PromptFormResult(
                traceId,
                ref(release),
                spec.objective(),
                spec.audience(),
                spec.variables(),
                spec.outputContract(),
                spec.knowledgeRequirements(),
                spec.knownLimitations());
    }

    public PromptRenderToolResult renderPrompt(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            Map<String, Object> variables) {
        PromptRenderResult rendered =
                prompts.render(actor, assetId, releaseId, variables);
        UUID traceId = traces.record(
                actor,
                AssistantAssetAction.RENDER_PROMPT,
                List.of(new AssistantReleaseRef(
                        assetId, releaseId, rendered.releaseDigest())),
                List.of(),
                Map.of(),
                Map.of("variableShape", variableShape(variables)),
                Map.of(
                        "inputShapeDigest", rendered.inputShapeDigest(),
                        "sensitiveVariableCount", rendered.sensitiveVariables().size()));
        return new PromptRenderToolResult(traceId, rendered);
    }

    public PromptRunToolResult runPrompt(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            Map<String, Object> variables,
            String knowledgeQuery,
            String requestId,
            boolean confirmedExternalProvider) {
        requireConfirmation(
                confirmedExternalProvider,
                "Running a Prompt sends approved content to an external model provider");
        PromptRunResult run = prompts.run(
                actor, assetId, releaseId, variables, knowledgeQuery, requestId);
        List<Map<String, Object>> citations = run.citations().stream()
                .map(citation -> Map.<String, Object>of(
                        "chunkId", citation.chunkId(),
                        "knowledgeAssetId", citation.knowledgeAssetId()))
                .toList();
        UUID traceId = traces.record(
                actor,
                AssistantAssetAction.RUN_PROMPT,
                List.of(new AssistantReleaseRef(
                        assetId, releaseId, run.releaseDigest())),
                citations,
                Map.of(
                        "gatewayId", run.modelRoute().gatewayId(),
                        "modelId", run.modelRoute().modelId()),
                Map.of(
                        "variableShape", variableShape(variables),
                        "knowledgeRequested", knowledgeQuery != null
                                && !knowledgeQuery.isBlank()),
                Map.of(
                        "runId", run.runId(),
                        "durationMillis", run.durationMillis(),
                        "outputDigest", sha256(run.output()),
                        "outputCharacters", run.output().length()));
        return new PromptRunToolResult(traceId, run);
    }

    public WorkInstructionToolResult followInstruction(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        WorkInstructionView instruction =
                instructions.follow(actor, assetId, releaseId);
        UUID traceId = traces.record(
                actor,
                AssistantAssetAction.FOLLOW_WORK_INSTRUCTION,
                List.of(new AssistantReleaseRef(
                        assetId, releaseId, instruction.releaseDigest())),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of("stepCount", instruction.instruction().steps().size()));
        return new WorkInstructionToolResult(traceId, instruction);
    }

    public PackToolResult startPack(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            boolean confirmed) {
        requireConfirmation(confirmed, "Starting a Pack creates an actor-scoped assignment");
        PackJourney journey = packs.start(actor, assetId, releaseId);
        return tracedPack(actor, AssistantAssetAction.START_PACK, journey, Map.of());
    }

    public PackToolResult readPack(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        PackJourney journey = packs.get(actor, assetId, releaseId);
        return tracedPack(actor, AssistantAssetAction.READ_PACK, journey, Map.of());
    }

    public PackToolResult updatePackProgress(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            String itemKey,
            boolean completed,
            boolean confirmed) {
        requireConfirmation(confirmed, "Updating a Pack changes your saved progress");
        PackJourney journey = packs.setItemCompleted(
                actor, assetId, releaseId, itemKey, completed);
        return tracedPack(
                actor,
                AssistantAssetAction.UPDATE_PACK_PROGRESS,
                journey,
                Map.of("itemKey", itemKey, "completed", completed));
    }

    public ForkResult fork(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            String namespace,
            String slug,
            UUID knowledgeSpaceId,
            boolean confirmed) {
        requireConfirmation(confirmed, "Forking creates a new mutable Asset draft");
        AssetConsumptionRelease source =
                assets.releaseForUse(actor, assetId, releaseId);
        AssetView fork = assets.forkRelease(
                actor, assetId, releaseId, namespace, slug, knowledgeSpaceId);
        UUID traceId = record(
                actor,
                AssistantAssetAction.FORK_RELEASE,
                source,
                Map.of("targetAssetId", fork.id()),
                Map.of("created", true));
        return new ForkResult(traceId, fork);
    }

    public FeedbackResult feedback(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            AssistantFeedbackType type,
            String comment,
            boolean confirmed) {
        requireConfirmation(confirmed, "Submitting feedback stores your comment for the Asset owner");
        AssetConsumptionRelease release =
                assets.releaseForUse(actor, assetId, releaseId);
        UUID feedbackId = traces.feedback(actor, release, type, comment);
        UUID traceId = record(
                actor,
                AssistantAssetAction.SUBMIT_FEEDBACK,
                release,
                Map.of("feedbackType", type.name()),
                Map.of("feedbackId", feedbackId, "commentDigest", sha256(comment)));
        return new FeedbackResult(traceId, feedbackId);
    }

    private PackToolResult tracedPack(
            CurrentActor actor,
            AssistantAssetAction action,
            PackJourney journey,
            Map<String, Object> call) {
        UUID traceId = traces.record(
                actor,
                action,
                List.of(new AssistantReleaseRef(
                        journey.packAssetId(),
                        journey.packReleaseId(),
                        journey.releaseDigest())),
                List.of(),
                Map.of(),
                call,
                Map.of(
                        "status", journey.status().name(),
                        "accessibleItems", journey.items().size(),
                        "accessGap", journey.accessGap()));
        return new PackToolResult(traceId, journey);
    }

    private UUID record(
            CurrentActor actor,
            AssistantAssetAction action,
            AssetConsumptionRelease release,
            Map<String, Object> call,
            Map<String, Object> outcome) {
        return traces.record(
                actor,
                action,
                List.of(ref(release)),
                List.of(),
                Map.of(),
                call,
                outcome);
    }

    private static AssistantReleaseRef ref(AssetConsumptionRelease release) {
        return new AssistantReleaseRef(
                release.assetId(), release.releaseId(), release.digest());
    }

    private static void requireConfirmation(boolean confirmed, String message) {
        if (!confirmed) {
            throw new BusinessValidationException(
                    "assistant.confirmation-required",
                    message + "; explicit confirmation is required");
        }
    }

    private static Map<String, String> variableShape(Map<String, Object> variables) {
        Map<String, String> shape = new java.util.TreeMap<>();
        if (variables != null) {
            variables.forEach((key, value) -> shape.put(
                    key, value == null ? "null" : value.getClass().getSimpleName()));
        }
        return Map.copyOf(shape);
    }

    private static Map<String, Object> shape(String value) {
        String normalized = value == null ? "" : value.strip();
        return Map.of(
                "present", !normalized.isEmpty(),
                "characters", normalized.length(),
                "digest", sha256(normalized));
    }

    private static String sha256(String value) {
        return com.orgmemory.core.shared.Digests.sha256(value == null ? "" : value);
    }

    public record RecommendationResult(
            UUID traceId, List<AssetRecommendation> recommendations) {
        public RecommendationResult {
            recommendations = List.copyOf(recommendations);
        }
    }

    public record KnowledgeResult(
            UUID traceId, String requestId, List<KnowledgeCitation> citations) {
        public KnowledgeResult {
            citations = List.copyOf(citations);
        }
    }

    public record KnowledgeCitation(
            UUID chunkId,
            UUID knowledgeAssetId,
            UUID sourceRevisionId,
            String title,
            Integer startPage,
            Integer endPage,
            String heading) {
    }

    public record PromptFormResult(
            UUID traceId,
            AssistantReleaseRef release,
            String objective,
            String audience,
            List<PromptTemplateSpec.Variable> variables,
            Map<String, Object> outputContract,
            List<String> knowledgeRequirements,
            String knownLimitations) {
        public PromptFormResult {
            variables = List.copyOf(variables);
            outputContract = Map.copyOf(outputContract);
            knowledgeRequirements = List.copyOf(knowledgeRequirements);
        }
    }

    public record PromptRenderToolResult(UUID traceId, PromptRenderResult result) {
    }

    public record PromptRunToolResult(UUID traceId, PromptRunResult result) {
    }

    public record WorkInstructionToolResult(
            UUID traceId, WorkInstructionView instruction) {
    }

    public record PackToolResult(UUID traceId, PackJourney journey) {
    }

    public record ForkResult(UUID traceId, AssetView asset) {
    }

    public record FeedbackResult(UUID traceId, UUID feedbackId) {
    }
}
