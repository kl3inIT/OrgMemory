package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourceRevisionRepository extends JpaRepository<SourceRevision, UUID> {

    Optional<SourceRevision> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
