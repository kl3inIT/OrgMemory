package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RawSourceObjectRepository extends JpaRepository<RawSourceObject, UUID> {

    Optional<RawSourceObject> findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectIdAndSourceVersion(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            String externalObjectId,
            String sourceVersion);

    Optional<RawSourceObject> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
