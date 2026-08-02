package com.orgmemory.core.ai;

import java.time.Instant;
import java.util.UUID;

public record AiGatewayProfileView(
        UUID id,
        String gatewayKey,
        String displayName,
        AiGatewayPreset preset,
        AiGatewayCategory category,
        AiGatewayProtocol protocol,
        boolean supportsOpenAiReasoningEffort,
        String baseUrl,
        int requestTimeoutSeconds,
        boolean enabled,
        long version,
        boolean credentialSet,
        UUID credentialSetByUserId,
        Instant credentialSetAt,
        UUID createdByUserId,
        UUID updatedByUserId,
        Instant createdAt,
        Instant updatedAt) {
}
