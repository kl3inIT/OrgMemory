package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourceGroupMembershipSnapshotRepository
        extends JpaRepository<SourceGroupMembershipSnapshot, UUID> {

    Optional<SourceGroupMembershipSnapshot>
            findFirstByOrganizationIdAndGroupPrincipalIdOrderByMembershipGenerationDesc(
                    UUID organizationId,
                    UUID groupPrincipalId);
}
