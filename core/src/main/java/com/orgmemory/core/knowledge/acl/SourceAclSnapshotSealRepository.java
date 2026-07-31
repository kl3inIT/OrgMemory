package com.orgmemory.core.knowledge.acl;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceAclSnapshotSealRepository extends JpaRepository<SourceAclSnapshotSeal, UUID> {

    boolean existsBySourceAclSnapshotIdAndOrganizationId(UUID sourceAclSnapshotId, UUID organizationId);
}
