package com.orgmemory.core.ai;

/**
 * Deployment-owned outbound policy. Organization administrators may select an
 * allowed model endpoint; they may not create a general-purpose HTTP proxy.
 */
public interface AiGatewayEndpointPolicy {

    String requireAllowed(
            AiGatewayPreset preset,
            AiGatewayProtocol protocol,
            String baseUrl);
}
