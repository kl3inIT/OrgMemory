package com.orgmemory.mcp;

import java.util.UUID;

final class McpValues {

    private McpValues() {
    }

    static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static int boundedLimit(
            Integer value,
            int defaultValue,
            int maximum,
            String subject) {
        int resolved = value == null ? defaultValue : value;
        if (resolved < 1 || resolved > maximum) {
            throw new McpGatewayException(
                    subject + " must be between 1 and " + maximum);
        }
        return resolved;
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
