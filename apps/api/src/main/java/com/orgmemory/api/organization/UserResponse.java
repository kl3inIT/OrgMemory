package com.orgmemory.api.organization;

import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.Clearance;
import java.util.UUID;

record UserResponse(
        UUID id,
        UUID organizationId,
        UUID departmentId,
        String name,
        String email,
        Clearance clearance
) {

    static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getOrganizationId(),
                user.getDepartmentId(),
                user.getName(),
                user.getEmail(),
                user.getClearance()
        );
    }
}
