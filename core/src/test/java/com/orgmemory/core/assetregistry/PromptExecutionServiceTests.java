package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.ChatGenerationRequest;
import com.orgmemory.core.ai.ChatModelPort;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.knowledge.search.PermissionAwareKnowledgeSearch;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

class PromptExecutionServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID RELEASE_ID = UUID.randomUUID();
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID, ORGANIZATION_ID, null, "User", "user@example.test");

    private final AssetRegistryService assets = mock(AssetRegistryService.class);
    private final PermissionAwareKnowledgeSearch knowledge =
            mock(PermissionAwareKnowledgeSearch.class);
    private final ChatModelPort chat = mock(ChatModelPort.class);
    private final AiRouteResolver routes = mock(AiRouteResolver.class);
    private final PromptRunCoordinator runs = mock(PromptRunCoordinator.class);
    private final PromptExecutionService service = new PromptExecutionService(
            assets,
            new PromptTemplateRenderer(new PromptTemplateProfile()),
            knowledge,
            chat,
            routes,
            runs);

    @Test
    void exactReleaseDigestRouteAndSanitizedOutcomeAreRecorded() {
        AiRoute route = new AiRoute("openai", "demo-model");
        UUID runId = UUID.randomUUID();
        when(assets.releaseForUse(
                        ACTOR, ASSET_ID, RELEASE_ID, AssetType.PROMPT_TEMPLATE))
                .thenReturn(release());
        when(routes.resolve(ORGANIZATION_ID, AiWorkload.PROMPT_EXECUTION))
                .thenReturn(route);
        when(runs.start(
                        eq(ACTOR),
                        any(),
                        eq(route),
                        any(),
                        eq("[]"),
                        any()))
                .thenReturn(runId);
        when(chat.stream(
                        eq(ORGANIZATION_ID),
                        eq(AiWorkload.PROMPT_EXECUTION),
                        eq(route),
                        any(ChatGenerationRequest.class)))
                .thenReturn(Flux.just("{\"category\":\"access\"}"));

        PromptRunResult result = service.run(
                ACTOR,
                ASSET_ID,
                RELEASE_ID,
                Map.of("ticket_text", "Customer cannot log in"),
                null,
                "request-1");

        assertEquals(runId, result.runId());
        assertEquals("d".repeat(64), result.releaseDigest());
        assertEquals(route, result.modelRoute());
        ArgumentCaptor<String> outcome = ArgumentCaptor.forClass(String.class);
        verify(runs).succeed(eq(runId), outcome.capture(), any(Instant.class));
        assertFalse(outcome.getValue().contains("access"));
    }

    @Test
    void invalidOutputContractFailsTheRunWithoutPersistingRawOutput() {
        AiRoute route = new AiRoute("openai", "demo-model");
        UUID runId = UUID.randomUUID();
        when(assets.releaseForUse(
                        ACTOR, ASSET_ID, RELEASE_ID, AssetType.PROMPT_TEMPLATE))
                .thenReturn(release());
        when(routes.resolve(ORGANIZATION_ID, AiWorkload.PROMPT_EXECUTION))
                .thenReturn(route);
        when(runs.start(
                        eq(ACTOR),
                        any(),
                        eq(route),
                        any(),
                        eq("[]"),
                        any()))
                .thenReturn(runId);
        when(chat.stream(
                        eq(ORGANIZATION_ID),
                        eq(AiWorkload.PROMPT_EXECUTION),
                        eq(route),
                        any(ChatGenerationRequest.class)))
                .thenReturn(Flux.just("not-json"));

        assertThrows(
                AssetUnavailableException.class,
                () -> service.run(
                        ACTOR,
                        ASSET_ID,
                        RELEASE_ID,
                        Map.of("ticket_text", "Customer cannot log in"),
                        null,
                        "request-2"));

        verify(runs).fail(
                eq(runId),
                eq("asset.unavailable"),
                any(Instant.class));
    }

    private static AssetConsumptionRelease release() {
        return new AssetConsumptionRelease(
                ASSET_ID,
                RELEASE_ID,
                UUID.randomUUID(),
                AssetType.PROMPT_TEMPLATE,
                "support",
                "triage",
                "1.0.0",
                AssetPublicationMode.REVIEWED,
                "Triage",
                "Triage a support ticket",
                "INTERNAL",
                "1",
                AssetProfileValidationTests.promptPayload(
                        "Classify: {{ticket_text}}"),
                "d".repeat(64),
                AssetAvailability.AVAILABLE,
                Instant.now());
    }
}
