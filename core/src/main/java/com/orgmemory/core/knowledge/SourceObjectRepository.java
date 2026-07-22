package com.orgmemory.core.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourceObjectRepository extends JpaRepository<SourceObject, UUID> {

    List<SourceObject> findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
            UUID organizationId, UUID createdByUserId);

    List<SourceObject> findAllByOrganizationIdAndIdInOrderByUpdatedAtDesc(
            UUID organizationId, Collection<UUID> ids);
}
