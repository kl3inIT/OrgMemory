package com.orgmemory.mcp;

import java.util.UUID;

final class McpValues {

    private McpValues() {
    }

    static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static UUID assetIdentifier(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new McpGatewayException(
                    "The Asset identifier is invalid", invalid);
        }
    }
}
