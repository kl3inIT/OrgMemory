package com.orgmemory.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class AssetDeliveryResources {

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
            uri = "orgmemory://assets/{assetId}",
            description = "Latest immutable release currently usable by the authenticated actor.",
            mimeType = "application/json")
    String getAsset(String assetId, McpTransportContext context) {
        return json.writeValueAsString(assets.getAsset(
                authorization.require(context), parse(assetId)));
    }

    @McpResource(
            name = "released-asset-version",
            title = "Exact released Asset version",
            uri = "orgmemory://assets/{assetId}/releases/{releaseId}",
            description = "Exact immutable release currently usable by the authenticated actor.",
            mimeType = "application/json")
    String getAssetRelease(
            String assetId,
            String releaseId,
            McpTransportContext context) {
        return json.writeValueAsString(assets.getRelease(
                authorization.require(context),
                parse(assetId),
                parse(releaseId)));
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
