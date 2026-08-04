package com.orgmemory.core.ai;

import java.util.UUID;

public interface AiRouteResolver {

    AiRoute resolve(AiWorkload workload);

    default AiRoute resolve(UUID organizationId, AiWorkload workload) {
        return resolve(workload);
    }

    /** Returns the selected route identity without requiring provider availability. */
    default AiRoute reference(UUID organizationId, AiWorkload workload) {
        return resolve(organizationId, workload);
    }
}
