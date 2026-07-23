package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourceConnectionCredentialRepository extends JpaRepository<SourceConnectionCredential, UUID> {

    Optional<SourceConnectionCredential> findByOrganizationIdAndSourceConnectionId(
            UUID organizationId, UUID sourceConnectionId);

    void deleteByOrganizationIdAndSourceConnectionId(UUID organizationId, UUID sourceConnectionId);
}
