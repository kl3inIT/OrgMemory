package com.orgmemory.integrations.ai.openai;

import com.orgmemory.core.ai.AiGatewayUnavailableException;
import com.orgmemory.core.ai.AiGatewayAdministrationService;
import com.orgmemory.core.ai.AiGatewayConnection;
import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class AiGatewayRegistry implements AiRouteResolver {

    private final AiGatewayProperties properties;
    private final AiGatewayAdministrationService administration;

    AiGatewayRegistry(
            AiGatewayProperties properties,
            AiGatewayAdministrationService administration) {
        this.properties = properties;
        this.administration = administration;
    }

    @Override
    public AiRoute resolve(AiWorkload workload) {
        AiRoute route = properties.route(workload);
        definition(null, workload, route);
        return route;
    }

    @Override
    public AiRoute resolve(UUID organizationId, AiWorkload workload) {
        Optional<com.orgmemory.core.ai.AiRouteOverrideView> override =
                administration.route(organizationId, workload);
        AiRoute route = override
                .map(com.orgmemory.core.ai.AiRouteOverrideView::route)
                .orElseGet(() -> properties.route(workload));
        definition(
                organizationId,
                workload,
                route,
                override.isPresent());
        return route;
    }

    ResolvedGateway definition(
            UUID organizationId,
            AiWorkload workload,
            AiRoute route) {
        boolean organizationOverride = false;
        if (organizationId != null) {
            Optional<com.orgmemory.core.ai.AiRouteOverrideView> selected =
                    administration.route(organizationId, workload);
            if (selected.isPresent()) {
                if (!selected.orElseThrow().route().equals(route)) {
                    throw new AiGatewayUnavailableException(
                            "The selected organization AI route is unavailable");
                }
                organizationOverride = true;
            }
        }
        return definition(
                organizationId,
                workload,
                route,
                organizationOverride);
    }

    private ResolvedGateway definition(
            UUID organizationId,
            AiWorkload workload,
            AiRoute route,
            boolean organizationOverride) {
        if (organizationOverride) {
            Optional<AiGatewayConnection> organizationConnection =
                    administration.connection(
                            organizationId,
                            route.gatewayId());
            if (organizationConnection.isEmpty()) {
                throw new AiGatewayUnavailableException(
                        "The selected organization AI gateway is unavailable");
            }
            AiGatewayConnection connection = organizationConnection.orElseThrow();
            return new ResolvedGateway(
                    connection.protocol(),
                    connection.baseUrl(),
                    connection.credential(),
                    connection.timeout(),
                    connection.profileVersion());
        }
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
        return new ResolvedGateway(
                AiGatewayProtocol.OPENAI_COMPATIBLE,
                gateway.baseUrl(),
                SecretValue.of(gateway.apiKey()),
                gateway.timeout(),
                0);
    }

    record ResolvedGateway(
            AiGatewayProtocol protocol,
            String baseUrl,
            SecretValue credential,
            Duration timeout,
            long profileVersion) {

        @Override
        public String toString() {
            return "ResolvedGateway[protocol=%s, baseUrl=%s, credential=<redacted>, timeout=%s, profileVersion=%d]"
                    .formatted(
                            protocol,
                            baseUrl,
                            timeout,
                            profileVersion);
        }
    }
}
