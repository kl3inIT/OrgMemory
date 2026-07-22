package com.orgmemory.core.ai;

public record AiRoute(String gatewayId, String modelId) {

    public AiRoute {
        gatewayId = required(gatewayId, "gatewayId").toLowerCase(java.util.Locale.ROOT);
        modelId = required(modelId, "modelId");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
