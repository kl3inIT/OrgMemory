package com.orgmemory.core.ai;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface AiAssistantModelActivationRepository
        extends JpaRepository<AiAssistantModelActivation, UUID> {

    List<AiAssistantModelActivation>
            findAllByOrganizationIdAndGatewayProfileIdAndEnabledTrueOrderByDisplayNameAscModelIdAsc(
                    UUID organizationId,
                    UUID gatewayProfileId);

    Optional<AiAssistantModelActivation>
            findByIdAndOrganizationIdAndEnabledTrue(
                    UUID id,
                    UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AiAssistantModelActivation>
            findAllByOrganizationIdAndGatewayProfileIdAndEnabledTrue(
                    UUID organizationId,
                    UUID gatewayProfileId);
}
