package com.orgmemory.core.ai;

public enum AiWorkload {
    ASSISTANT_CHAT(AiGatewayCapability.CHAT),
    GRAPH_EXTRACTION(AiGatewayCapability.CHAT),
    QUERY_EMBEDDING(AiGatewayCapability.EMBEDDING),
    DOCUMENT_EMBEDDING(AiGatewayCapability.EMBEDDING);

    private final AiGatewayCapability requiredCapability;

    AiWorkload(AiGatewayCapability requiredCapability) {
        this.requiredCapability = requiredCapability;
    }

    public AiGatewayCapability requiredCapability() {
        return requiredCapability;
    }
}
