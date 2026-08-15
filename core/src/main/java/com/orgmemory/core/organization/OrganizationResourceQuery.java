package com.orgmemory.core.organization;

import java.util.Optional;
import java.util.UUID;

/** Organization-owned canonical existence checks for authorization resource resolution. */
public interface OrganizationResourceQuery {

    boolean organizationExists(UUID organizationId);

    boolean departmentExists(UUID organizationId, UUID departmentId);

    Optional<String> findOrganizationName(UUID organizationId);

    Optional<String> findDepartmentName(UUID organizationId, UUID departmentId);
}
