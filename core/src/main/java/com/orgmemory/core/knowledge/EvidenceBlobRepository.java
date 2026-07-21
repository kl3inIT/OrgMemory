package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EvidenceBlobRepository extends JpaRepository<EvidenceBlob, UUID> {

    Optional<EvidenceBlob> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
