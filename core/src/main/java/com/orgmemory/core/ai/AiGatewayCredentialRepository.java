package com.orgmemory.core.ai;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiGatewayCredentialRepository
        extends JpaRepository<AiGatewayCredential, UUID> {

    Optional<AiGatewayCredential>
            findByOrganizationIdAndGatewayProfileId(
                    UUID organizationId,
                    UUID gatewayProfileId);
}
