package com.orgmemory.core.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.assetregistry.api.AssetPortfolioState;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.AssetAvailability;
import com.orgmemory.core.assetregistry.AssetRecommendation;
import com.orgmemory.core.assetregistry.AssetRegistryService;
import com.orgmemory.core.assetregistry.CapabilityPackService;
import com.orgmemory.core.assetregistry.PromptExecutionService;
import com.orgmemory.core.assetregistry.PromptRunResult;
import com.orgmemory.core.assetregistry.PromptTemplateRenderer;
import com.orgmemory.core.assetregistry.WorkInstructionService;
import com.orgmemory.core.knowledge.search.PermissionAwareKnowledgeSearch;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssistantAssetToolServiceTests {

    private final AssetRegistryService assets = mock(AssetRegistryService.class);
    private final PromptExecutionService prompts = mock(PromptExecutionService.class);
    private final PromptTemplateRenderer renderer = mock(PromptTemplateRenderer.class);
    private final WorkInstructionService instructions = mock(WorkInstructionService.class);
    private final CapabilityPackService packs = mock(CapabilityPackService.class);
    private final PermissionAwareKnowledgeSearch knowledge =
            mock(PermissionAwareKnowledgeSearch.class);
    private final AssistantAssetTraceRecorder traces =
            mock(AssistantAssetTraceRecorder.class);
    private final CurrentActor actor = new CurrentActor(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Alex",
            "alex@example.test");
    private AssistantAssetToolService service;

    @BeforeEach
    void setUp() {
        service = new AssistantAssetToolService(
                assets,
                prompts,
                renderer,
                instructions,
                packs,
                knowledge,
                traces);
        when(traces.record(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(UUID.randomUUID());
    }

    @Test
    void recommendationsContainOnlyExactUsableReleaseRefs() {
        UUID assetId = UUID.randomUUID();
        UUID releaseId = UUID.randomUUID();
        when(assets.recommend(actor, "triage", AssetType.PROMPT_TEMPLATE))
                .thenReturn(List.of(new AssetRecommendation(
                        assetId,
                        AssetType.PROMPT_TEMPLATE,
                        "support",
                        "triage",
                        "Triage ticket",
                        "Approved support flow",
                        UUID.randomUUID(),
                        AssetPortfolioState.ACTIVE,
                        releaseId,
                        "1.0.0",
                        "a".repeat(64),
                        AssetAvailability.AVAILABLE,
                        Instant.parse("2026-07-28T00:00:00Z"))));

        var result = service.recommend(
                actor, "triage", AssetType.PROMPT_TEMPLATE);

        assertEquals(releaseId, result.recommendations().getFirst().releaseId());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssistantReleaseRef>> refs =
                ArgumentCaptor.forClass(List.class);
        verify(traces).record(
                eq(actor),
                eq(AssistantAssetAction.RECOMMEND_ASSETS),
                refs.capture(),
                eq(List.of()),
                eq(Map.of()),
                any(),
                eq(Map.of("resultCount", 1)));
        assertEquals(
                new AssistantReleaseRef(assetId, releaseId, "a".repeat(64)),
                refs.getValue().getFirst());
    }

    @Test
    void promptRunRequiresExplicitProviderConfirmation() {
        assertThrows(
                BusinessValidationException.class,
                () -> service.runPrompt(
                        actor,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Map.of("ticket", "SECRET"),
                        null,
                        "request-1",
                        false));

        verify(prompts, never()).run(any(), any(), any(), any(), any(), any());
    }

    @Test
    void promptTraceStoresShapeAndDigestButNoRawSecretOrOutput() {
        UUID assetId = UUID.randomUUID();
        UUID releaseId = UUID.randomUUID();
        PromptRunResult run = new PromptRunResult(
                UUID.randomUUID(),
                assetId,
                releaseId,
                "b".repeat(64),
                new AiRoute("gateway", "model"),
                "SECRET GENERATED OUTPUT",
                List.of(),
                25);
        when(prompts.run(
                        eq(actor),
                        eq(assetId),
                        eq(releaseId),
                        any(),
                        eq(null),
                        eq("request-2")))
                .thenReturn(run);

        service.runPrompt(
                actor,
                assetId,
                releaseId,
                Map.of("ticket", "SECRET INPUT"),
                null,
                "request-2",
                true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> toolCall =
                ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> outcome =
                ArgumentCaptor.forClass(Map.class);
        verify(traces).record(
                eq(actor),
                eq(AssistantAssetAction.RUN_PROMPT),
                any(),
                eq(List.of()),
                eq(Map.of("gatewayId", "gateway", "modelId", "model")),
                toolCall.capture(),
                outcome.capture());
        String persisted = toolCall.getValue() + " " + outcome.getValue();
        assertFalse(persisted.contains("SECRET INPUT"));
        assertFalse(persisted.contains("SECRET GENERATED OUTPUT"));
        assertEquals("String", ((Map<?, ?>)
                toolCall.getValue().get("variableShape")).get("ticket"));
        assertEquals(23, outcome.getValue().get("outputCharacters"));
    }

    @Test
    void assistantActionRegistryHasNoGovernanceOrArbitraryExecutionPath() {
        String actions = List.of(AssistantAssetAction.values()).toString();
        assertFalse(actions.contains("APPROVE"));
        assertFalse(actions.contains("PUBLISH"));
        assertFalse(actions.contains("WITHDRAW"));
        assertFalse(actions.contains("ROLE"));
        assertFalse(actions.contains("PERMISSION"));
        assertFalse(actions.contains("EXECUTE"));
    }
}
