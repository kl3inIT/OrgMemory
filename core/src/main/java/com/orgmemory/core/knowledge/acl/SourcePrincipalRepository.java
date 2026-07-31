package com.orgmemory.core.knowledge.acl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourcePrincipalRepository extends JpaRepository<SourcePrincipal, UUID> {

    Optional<SourcePrincipal>
            findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndKindAndNativePrincipalId(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            SourcePrincipalKind kind,
            String nativePrincipalId);

    Optional<SourcePrincipal> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<SourcePrincipal> findByOrganizationId(UUID organizationId);
}
