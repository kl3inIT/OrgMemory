package com.orgmemory.core.organization;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class JpaOrganizationResourceQuery implements OrganizationResourceQuery {

    private final OrganizationRepository organizations;
    private final DepartmentRepository departments;

    JpaOrganizationResourceQuery(
            OrganizationRepository organizations,
            DepartmentRepository departments) {
        this.organizations = organizations;
        this.departments = departments;
    }

    @Override
    public boolean organizationExists(UUID organizationId) {
        return organizations.existsById(
                Objects.requireNonNull(organizationId, "organizationId"));
    }

    @Override
    public boolean departmentExists(
            UUID organizationId,
            UUID departmentId) {
        return departments.existsByIdAndOrganizationId(
                Objects.requireNonNull(departmentId, "departmentId"),
                Objects.requireNonNull(organizationId, "organizationId"));
    }
}
