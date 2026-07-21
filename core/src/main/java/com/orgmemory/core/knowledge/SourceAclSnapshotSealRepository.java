package com.orgmemory.core.knowledge;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourceAclSnapshotSealRepository extends JpaRepository<SourceAclSnapshotSeal, UUID> {

    boolean existsBySourceAclSnapshotIdAndOrganizationId(UUID sourceAclSnapshotId, UUID organizationId);
}
