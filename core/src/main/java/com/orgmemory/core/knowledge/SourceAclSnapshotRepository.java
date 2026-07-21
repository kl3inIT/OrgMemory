package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourceAclSnapshotRepository extends JpaRepository<SourceAclSnapshot, UUID> {

    Optional<SourceAclSnapshot> findFirstByRawSourceObjectIdOrderByAclGenerationAsc(UUID rawSourceObjectId);

    Optional<SourceAclSnapshot> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
