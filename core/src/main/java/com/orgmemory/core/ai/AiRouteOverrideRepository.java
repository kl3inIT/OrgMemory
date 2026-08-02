package com.orgmemory.core.ai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiRouteOverrideRepository
        extends JpaRepository<AiRouteOverride, UUID> {

    List<AiRouteOverride> findByOrganizationIdOrderByWorkloadAsc(
            UUID organizationId);

    Optional<AiRouteOverride> findByOrganizationIdAndWorkload(
            UUID organizationId,
            AiWorkload workload);

    boolean existsByOrganizationIdAndGatewayProfileId(
            UUID organizationId,
            UUID gatewayProfileId);

    boolean existsByOrganizationIdAndGatewayProfileIdAndOpenAiReasoningEffortIsNotNull(
            UUID organizationId,
            UUID gatewayProfileId);
}
