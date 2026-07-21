package com.orgmemory.core.organization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findByOrganizationIdOrderByName(UUID organizationId);

    boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);
}
