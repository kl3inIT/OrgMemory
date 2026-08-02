package com.orgmemory.core.ai;

import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Duration;
import java.util.UUID;

/**
 * Server-only resolved connection. This record must never be used as an API
 * response or logged because it carries the decrypted credential.
 */
public record AiGatewayConnection(
        UUID organizationId,
        UUID profileId,
        String gatewayKey,
        AiGatewayProtocol protocol,
        boolean supportsOpenAiReasoningEffort,
        String baseUrl,
        SecretValue credential,
        Duration timeout,
        long profileVersion) {

    public AiGatewayConnection(
            UUID organizationId,
            UUID profileId,
            String gatewayKey,
            AiGatewayProtocol protocol,
            String baseUrl,
            SecretValue credential,
            Duration timeout,
            long profileVersion) {
        this(
                organizationId,
                profileId,
                gatewayKey,
                protocol,
                false,
                baseUrl,
                credential,
                timeout,
                profileVersion);
    }

    @Override
    public String toString() {
        return "AiGatewayConnection[organizationId=%s, profileId=%s, gatewayKey=%s, protocol=%s, supportsOpenAiReasoningEffort=%s, baseUrl=%s, credential=<redacted>, timeout=%s, profileVersion=%d]"
                .formatted(
                        organizationId,
                        profileId,
                        gatewayKey,
                        protocol,
                        supportsOpenAiReasoningEffort,
                        baseUrl,
                        timeout,
                        profileVersion);
    }
}
