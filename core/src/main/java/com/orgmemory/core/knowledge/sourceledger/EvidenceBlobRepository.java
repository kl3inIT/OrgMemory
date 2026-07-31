package com.orgmemory.core.knowledge.sourceledger;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceBlobRepository extends JpaRepository<EvidenceBlob, UUID> {

    Optional<EvidenceBlob> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
