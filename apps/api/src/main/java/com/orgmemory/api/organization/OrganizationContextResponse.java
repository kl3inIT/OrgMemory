package com.orgmemory.api.organization;

import java.util.List;
import java.util.UUID;

record OrganizationContextResponse(
        UUID organizationId,
        List<DepartmentResponse> departments,
        List<UserResponse> users
) {
}
