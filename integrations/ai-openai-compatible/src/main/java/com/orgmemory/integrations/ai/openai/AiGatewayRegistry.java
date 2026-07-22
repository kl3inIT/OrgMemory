package com.orgmemory.integrations.ai.openai;

import com.orgmemory.core.ai.AiGatewayUnavailableException;
import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import org.springframework.stereotype.Component;

@Component
final class AiGatewayRegistry implements AiRouteResolver {

    private final AiGatewayProperties properties;

    AiGatewayRegistry(AiGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public AiRoute resolve(AiWorkload workload) {
        AiRoute route = properties.route(workload);
        definition(workload, route);
        return route;
    }

    AiGatewayProperties.Gateway definition(AiWorkload workload, AiRoute route) {
        AiGatewayProperties.Gateway gateway = properties.gateways().get(route.gatewayId());
        if (gateway == null) {
            throw new AiGatewayUnavailableException("Unknown AI gateway: " + route.gatewayId());
        }
        if (!gateway.configured()) {
            throw new AiGatewayUnavailableException("AI gateway is not configured: " + route.gatewayId());
        }
        if (!gateway.capabilities().contains(workload.requiredCapability())) {
            throw new AiGatewayUnavailableException(
                    "AI gateway does not support " + workload.requiredCapability());
        }
        if (route.modelId().isBlank()) {
            throw new AiGatewayUnavailableException("AI route model is not configured for " + workload);
        }
        return gateway;
    }
}
