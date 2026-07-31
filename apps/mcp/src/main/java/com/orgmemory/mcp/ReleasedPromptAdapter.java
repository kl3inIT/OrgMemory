package com.orgmemory.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
class ReleasedPromptAdapter {

    static final String PROMPT_NAME = "released_prompt";
    static final String ASSET_ID_ARGUMENT = "asset_id";
    static final String RELEASE_ID_ARGUMENT = "release_id";

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
            name = PROMPT_NAME,
            title = "Use an approved OrgMemory Prompt release",
            description = "Compiles a pinned, authorized Prompt release for the MCP host to execute; OrgMemory does not invoke an AI provider.")
    GetPromptResult releasedPrompt(
            @McpArg(
                            name = ASSET_ID_ARGUMENT,
                            description = "Prompt Template Asset UUID")
                    String assetId,
            @McpArg(
                            name = RELEASE_ID_ARGUMENT,
                            description = "Pinned Prompt release UUID")
                    String releaseId,
            @McpArg(
                            name = "variables_json",
                            description = "JSON object matching the Prompt variable contract",
                            required = false)
                    String variablesJson,
            McpTransportContext context) {
        AssetDeliveryApiClient.PromptRender rendered =
                McpFailureBoundary.sanitized(() -> assets.renderPrompt(
                        authorization.require(context),
                        McpValues.assetIdentifier(assetId),
                        McpValues.assetIdentifier(releaseId),
                        variables(variablesJson)));
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
            Map<String, Object> parsed =
                    json.readValue(variablesJson, VARIABLE_MAP);
            if (parsed == null) {
                throw new IllegalArgumentException(
                        "variables_json root must be an object");
            }
            return Collections.unmodifiableMap(
                    new LinkedHashMap<>(parsed));
        } catch (RuntimeException invalid) {
            throw new McpGatewayException(
                    "variables_json must be a JSON object");
        }
    }

}
