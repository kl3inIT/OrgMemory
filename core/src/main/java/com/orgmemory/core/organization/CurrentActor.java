package com.orgmemory.core.organization;

import com.orgmemory.core.authorization.PrincipalRef;
import java.util.UUID;

public record CurrentActor(
        UUID userId,
        UUID organizationId,
        UUID departmentId,
        String name,
        String email,
        Clearance clearance) {

    public CurrentActor(
            UUID userId,
            UUID organizationId,
            UUID departmentId,
            String name,
            String email) {
        this(userId, organizationId, departmentId, name, email, null);
    }

    public PrincipalRef principal() {
        return PrincipalRef.user(userId);
    }
}
