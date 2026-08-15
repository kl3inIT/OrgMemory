package com.orgmemory.core.organization;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findByOrganizationIdOrderByName(UUID organizationId);

    List<Department> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

    boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Department> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
