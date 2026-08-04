package com.orgmemory.core.ai;

import java.util.UUID;

public record AiAssistantModelActivationView(
        UUID id,
        UUID organizationId,
        UUID gatewayProfileId,
        String modelId,
        String displayName,
        boolean enabled,
        long version) {
}
