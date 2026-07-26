package com.orgmemory.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
class AssetDeliveryTools {

    private final AssetDeliveryApiClient assets;
    private final McpApiAuthorization authorization;

    AssetDeliveryTools(
            AssetDeliveryApiClient assets,
            McpApiAuthorization authorization) {
        this.assets = assets;
        this.authorization = authorization;
    }

    @McpTool(
            name = "search_assets",
            title = "Search released Assets",
            description = "Searches only exact released Assets the authenticated actor may use.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    title = "Search released Assets",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    List<AssetLink> searchAssets(
            @McpToolParam(required = false, description = "Optional title, summary, namespace, or slug query")
                    String query,
            @McpToolParam(
                            required = false,
                            description = "Optional PROMPT_TEMPLATE, WORK_INSTRUCTION, or CAPABILITY_PACK filter")
                    String type,
            McpTransportContext context) {
        return assets.search(authorization.require(context), query, type)
                .stream()
                .map(AssetLink::from)
                .toList();
    }

    @McpTool(
            name = "get_asset",
            title = "Get latest released Asset",
            description = "Gets the latest usable immutable release without draft or governance data.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    title = "Get latest released Asset",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    AssetDeliveryApiClient.AssetRelease getAsset(
            @McpToolParam(description = "Registry Asset UUID") UUID assetId,
            McpTransportContext context) {
        return assets.getAsset(authorization.require(context), assetId);
    }

    @McpTool(
            name = "get_asset_release",
            title = "Get exact Asset release",
            description = "Gets one exact authorized immutable Asset release.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    title = "Get exact Asset release",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    AssetDeliveryApiClient.AssetRelease getAssetRelease(
            @McpToolParam(description = "Registry Asset UUID") UUID assetId,
            @McpToolParam(description = "Pinned release UUID") UUID releaseId,
            McpTransportContext context) {
        return assets.getRelease(
                authorization.require(context), assetId, releaseId);
    }

    @McpTool(
            name = "get_capability_pack",
            title = "Get Capability Pack",
            description = "Gets an exact Pack and only its independently authorized pinned items.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    title = "Get Capability Pack",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    AssetDeliveryApiClient.PackDefinition getCapabilityPack(
            @McpToolParam(description = "Capability Pack Asset UUID") UUID assetId,
            @McpToolParam(description = "Pinned Capability Pack release UUID")
                    UUID releaseId,
            McpTransportContext context) {
        return assets.getPack(
                authorization.require(context), assetId, releaseId);
    }

    @McpTool(
            name = "resolve_asset_relations",
            title = "Resolve Asset relations",
            description = "Resolves only independently authorized relations and reports an opaque access gap.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    title = "Resolve Asset relations",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    AssetDeliveryApiClient.RelationResolution resolveAssetRelations(
            @McpToolParam(description = "Registry Asset UUID") UUID assetId,
            @McpToolParam(description = "Pinned release UUID") UUID releaseId,
            McpTransportContext context) {
        return assets.getRelations(
                authorization.require(context), assetId, releaseId);
    }

    @McpTool(
            name = "render_prompt",
            title = "Render released Prompt",
            description = "Deterministically renders an exact authorized Prompt; it never calls an AI provider.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    title = "Render released Prompt",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    AssetDeliveryApiClient.PromptRender renderPrompt(
            @McpToolParam(description = "Prompt Template Asset UUID") UUID assetId,
            @McpToolParam(description = "Pinned Prompt release UUID") UUID releaseId,
            @McpToolParam(
                            required = false,
                            description = "Values for the Prompt release variable contract")
                    Map<String, Object> variables,
            McpTransportContext context) {
        return assets.renderPrompt(
                authorization.require(context),
                assetId,
                releaseId,
                variables);
    }

    record AssetLink(
            AssetDeliveryApiClient.AssetSummary asset,
            String resourceUri,
            String releaseResourceUri) {

        static AssetLink from(
                AssetDeliveryApiClient.AssetSummary asset) {
            String base = "orgmemory://assets/" + asset.assetId();
            return new AssetLink(
                    asset,
                    base,
                    base + "/releases/" + asset.releaseId());
        }
    }
}
