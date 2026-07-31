package com.orgmemory.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class AssetDeliveryResources {

    static final String ASSET_URI = "orgmemory://assets/{assetId}";
    static final String ASSET_RELEASE_URI =
            "orgmemory://assets/{assetId}/releases/{releaseId}";
    static final String ASSET_ID_VARIABLE = "assetId";
    static final String RELEASE_ID_VARIABLE = "releaseId";

    private final AssetDeliveryApiClient assets;
    private final McpApiAuthorization authorization;
    private final ObjectMapper json;

    AssetDeliveryResources(
            AssetDeliveryApiClient assets,
            McpApiAuthorization authorization,
            ObjectMapper json) {
        this.assets = assets;
        this.authorization = authorization;
        this.json = json;
    }

    @McpResource(
            name = "released-asset",
            title = "Latest released Asset",
            uri = ASSET_URI,
            description = "Latest immutable release currently usable by the authenticated actor.",
            mimeType = "application/json")
    String getAsset(String assetId, McpTransportContext context) {
        return McpFailureBoundary.sanitized(() ->
                json.writeValueAsString(assets.getAsset(
                        authorization.require(context),
                        McpValues.assetIdentifier(assetId))));
    }

    @McpResource(
            name = "released-asset-version",
            title = "Exact released Asset version",
            uri = ASSET_RELEASE_URI,
            description = "Exact immutable release currently usable by the authenticated actor.",
            mimeType = "application/json")
    String getAssetRelease(
            String assetId,
            String releaseId,
            McpTransportContext context) {
        return McpFailureBoundary.sanitized(() ->
                json.writeValueAsString(assets.getRelease(
                        authorization.require(context),
                        McpValues.assetIdentifier(assetId),
                        McpValues.assetIdentifier(releaseId))));
    }

}
