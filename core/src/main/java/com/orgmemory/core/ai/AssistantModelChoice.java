package com.orgmemory.core.ai;

import java.util.UUID;

public record AssistantModelChoice(
        UUID activationId,
        String gatewayLabel,
        String provider,
        String modelId,
        String displayName,
        boolean defaultChoice) {
}
