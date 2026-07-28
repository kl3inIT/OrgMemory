package com.orgmemory.core.ai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiGatewayProfileRepository
        extends JpaRepository<AiGatewayProfile, UUID> {

    List<AiGatewayProfile> findByOrganizationIdOrderByDisplayNameAsc(
            UUID organizationId);

    Optional<AiGatewayProfile> findByIdAndOrganizationId(
            UUID id,
            UUID organizationId);

    Optional<AiGatewayProfile> findByOrganizationIdAndGatewayKey(
            UUID organizationId,
            String gatewayKey);
}
