package com.orgmemory.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
class ReleasedPromptAdapter {

    private static final TypeReference<Map<String, Object>> VARIABLE_MAP =
            new TypeReference<>() {
            };

    private final AssetDeliveryApiClient assets;
    private final McpApiAuthorization authorization;
    private final ObjectMapper json;

    ReleasedPromptAdapter(
            AssetDeliveryApiClient assets,
            McpApiAuthorization authorization,
            ObjectMapper json) {
        this.assets = assets;
        this.authorization = authorization;
        this.json = json;
    }

    @McpPrompt(
            name = "released_prompt",
            title = "Use an approved OrgMemory Prompt release",
            description = "Compiles a pinned, authorized Prompt release for the MCP host to execute; OrgMemory does not invoke an AI provider.")
    GetPromptResult releasedPrompt(
            @McpArg(name = "asset_id", description = "Prompt Template Asset UUID")
                    String assetId,
            @McpArg(name = "release_id", description = "Pinned Prompt release UUID")
                    String releaseId,
            @McpArg(
                            name = "variables_json",
                            description = "JSON object matching the Prompt variable contract",
                            required = false)
                    String variablesJson,
            McpTransportContext context) {
        AssetDeliveryApiClient.PromptRender rendered = assets.renderPrompt(
                authorization.require(context),
                parse(assetId),
                parse(releaseId),
                variables(variablesJson));
        String content = """
                Approved system instruction:
                %s

                User prompt:
                %s

                Release digest: %s
                """
                .formatted(
                        rendered.systemInstruction(),
                        rendered.userPrompt(),
                        rendered.releaseDigest())
                .strip();
        PromptMessage message = PromptMessage.builder(
                        Role.USER, TextContent.builder(content).build())
                .build();
        return GetPromptResult.builder(List.of(message))
                .description(
                        "Rendered from an exact authorized OrgMemory Prompt release.")
                .build();
    }

    private Map<String, Object> variables(String variablesJson) {
        if (variablesJson == null || variablesJson.isBlank()) {
            return Map.of();
        }
        try {
            return Map.copyOf(json.readValue(variablesJson, VARIABLE_MAP));
        } catch (RuntimeException invalid) {
            throw new AssetDeliveryApiClient.AssetDeliveryGatewayException(
                    "variables_json must be a JSON object");
        }
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new AssetDeliveryApiClient.AssetDeliveryGatewayException(
                    "The Asset identifier is invalid");
        }
    }
}
