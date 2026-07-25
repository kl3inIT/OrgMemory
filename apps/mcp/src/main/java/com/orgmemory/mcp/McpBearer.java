package com.orgmemory.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import java.util.Objects;

final class McpBearer {

    private McpBearer() {
    }

    static String require(McpTransportContext context) {
        String authorization = Objects.toString(
                context.get(
                        McpTransportConfiguration.AUTHORIZATION_CONTEXT_KEY),
                "");
        if (authorization.isBlank()) {
            throw new AssetDeliveryApiClient.AssetDeliveryGatewayException(
                    "The MCP request has no authenticated identity");
        }
        return authorization;
    }
}
