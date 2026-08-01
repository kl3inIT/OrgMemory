package com.orgmemory.core.organization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JpaOrganizationResourceQueryTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();

    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final JpaOrganizationResourceQuery query =
            new JpaOrganizationResourceQuery(organizations, departments);

    @Test
    void organizationExistenceStaysOwnedByOrganizationPersistence() {
        when(organizations.existsById(ORGANIZATION_ID)).thenReturn(true);

        assertTrue(query.organizationExists(ORGANIZATION_ID));
    }

    @Test
    void departmentExistenceKeepsDepartmentBeforeOrganizationRepositoryOrderInternal() {
        when(departments.existsByIdAndOrganizationId(DEPARTMENT_ID, ORGANIZATION_ID))
                .thenReturn(true);

        assertTrue(query.departmentExists(ORGANIZATION_ID, DEPARTMENT_ID));
        verify(departments).existsByIdAndOrganizationId(DEPARTMENT_ID, ORGANIZATION_ID);
    }

    @Test
    void missingResourcesRemainAbsent() {
        assertFalse(query.organizationExists(ORGANIZATION_ID));
        assertFalse(query.departmentExists(ORGANIZATION_ID, DEPARTMENT_ID));
    }
}
