package com.orgmemory.integrations.ai.gateway;

import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiWorkload;
import java.util.Objects;
import java.util.UUID;

record ModelKey(
        UUID organizationId,
        AiWorkload workload,
        AiRoute route,
        AiGatewayProtocol protocol,
        long profileVersion) {

    boolean supersededBy(ModelKey active) {
        return Objects.equals(organizationId, active.organizationId)
                && (workload == active.workload
                        || (route.gatewayId().equals(active.route.gatewayId())
                                && profileVersion < active.profileVersion))
                && !equals(active);
    }
}
