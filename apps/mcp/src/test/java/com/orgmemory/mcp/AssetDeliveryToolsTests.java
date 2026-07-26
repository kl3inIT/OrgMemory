package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.common.McpTransportContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

class AssetDeliveryToolsTests {

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID RELEASE_ID = UUID.randomUUID();

    private final AssetDeliveryApiClient client =
            mock(AssetDeliveryApiClient.class);
    private final McpApiAuthorization authorization =
            mock(McpApiAuthorization.class);
    private final AssetDeliveryTools tools =
            new AssetDeliveryTools(client, authorization);
    private final McpTransportContext context =
            McpTransportContext.create(Map.of("request", "test"));

    @Test
    void delegatesExactReleaseReadsWithTheForwardedActor() {
        var expected = mock(AssetDeliveryApiClient.AssetRelease.class);
        when(authorization.require(context))
                .thenReturn("Bearer exchanged-api-token");
        when(client.getRelease(
                        "Bearer exchanged-api-token", ASSET_ID, RELEASE_ID))
                .thenReturn(expected);

        var result =
                tools.getAssetRelease(ASSET_ID, RELEASE_ID, context);

        assertEquals(expected, result);
        verify(client).getRelease(
                "Bearer exchanged-api-token", ASSET_ID, RELEASE_ID);
    }

    @Test
    void searchResultsCarryLatestAndPinnedResourceUris() {
        var summary = new AssetDeliveryApiClient.AssetSummary(
                ASSET_ID,
                "PROMPT_TEMPLATE",
                "support",
                "triage",
                "Triage",
                "Approved triage prompt",
                UUID.randomUUID(),
                "RELEASED",
                RELEASE_ID,
                "1.0.0",
                "sha256:digest",
                "AVAILABLE");
        when(authorization.require(context))
                .thenReturn("Bearer exchanged-api-token");
        when(client.search(
                        "Bearer exchanged-api-token",
                        "triage",
                        "PROMPT_TEMPLATE"))
                .thenReturn(List.of(summary));

        var result = tools.searchAssets(
                "triage",
                "PROMPT_TEMPLATE",
                context);

        assertEquals(
                "orgmemory://assets/" + ASSET_ID,
                result.getFirst().resourceUri());
        assertEquals(
                "orgmemory://assets/"
                        + ASSET_ID
                        + "/releases/"
                        + RELEASE_ID,
                result.getFirst().releaseResourceUri());
    }

    @Test
    void publishesSixExplicitReadOnlyClosedWorldTools() {
        List<McpTool> declarations = java.util.Arrays.stream(
                        AssetDeliveryTools.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(McpTool.class))
                .filter(java.util.Objects::nonNull)
                .toList();

        assertEquals(6, declarations.size());
        assertEquals(
                List.of(
                        "get_asset",
                        "get_asset_release",
                        "get_capability_pack",
                        "render_prompt",
                        "resolve_asset_relations",
                        "search_assets"),
                declarations.stream().map(McpTool::name).sorted().toList());
        declarations.forEach(declaration -> {
            assertEquals(true, declaration.annotations().readOnlyHint());
            assertEquals(true, declaration.annotations().idempotentHint());
            assertFalse(declaration.annotations().destructiveHint());
            assertFalse(declaration.annotations().openWorldHint());
        });
    }
}
