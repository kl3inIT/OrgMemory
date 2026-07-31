package com.orgmemory.core.knowledge.acl;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceGroupMembershipSnapshotRepository
        extends JpaRepository<SourceGroupMembershipSnapshot, UUID> {

    Optional<SourceGroupMembershipSnapshot>
            findFirstByOrganizationIdAndGroupPrincipalIdOrderByMembershipGenerationDesc(
                    UUID organizationId,
                    UUID groupPrincipalId);
}
