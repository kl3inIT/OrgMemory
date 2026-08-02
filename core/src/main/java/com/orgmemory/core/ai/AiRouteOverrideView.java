package com.orgmemory.core.ai;

import java.time.Instant;
import java.util.UUID;

public record AiRouteOverrideView(
        UUID id,
        AiWorkload workload,
        UUID gatewayProfileId,
        String gatewayKey,
        String modelId,
        OpenAiReasoningEffort openAiReasoningEffort,
        long version,
        UUID setByUserId,
        Instant setAt) {

    public AiRoute route() {
        return new AiRoute(gatewayKey, modelId, openAiReasoningEffort);
    }

    public AiRouteOverrideView(
            UUID id,
            AiWorkload workload,
            UUID gatewayProfileId,
            String gatewayKey,
            String modelId,
            long version,
            UUID setByUserId,
            Instant setAt) {
        this(
                id,
                workload,
                gatewayProfileId,
                gatewayKey,
                modelId,
                null,
                version,
                setByUserId,
                setAt);
    }
}
