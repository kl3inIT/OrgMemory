package com.orgmemory.api.organization;

import com.orgmemory.core.organization.Department;
import java.util.UUID;

record DepartmentResponse(UUID id, UUID organizationId, String name) {

    static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getOrganizationId(),
                department.getName()
        );
    }
}
