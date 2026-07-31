package com.orgmemory.core.knowledge.sourceledger;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawSourceObjectRepository extends JpaRepository<RawSourceObject, UUID> {

    Optional<RawSourceObject> findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectIdAndSourceVersion(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            String externalObjectId,
            String sourceVersion);

    Optional<RawSourceObject> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
