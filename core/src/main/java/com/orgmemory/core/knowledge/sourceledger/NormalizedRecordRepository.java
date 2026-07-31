package com.orgmemory.core.knowledge.sourceledger;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NormalizedRecordRepository extends JpaRepository<NormalizedRecord, UUID> {

    Optional<NormalizedRecord> findByRawSourceObjectIdAndNormalizerVersion(
            UUID rawSourceObjectId,
            String normalizerVersion);

    Optional<NormalizedRecord> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
