package com.orgmemory.core.organization;

import java.util.UUID;

/** Organization-owned canonical existence checks for authorization resource resolution. */
public interface OrganizationResourceQuery {

    boolean organizationExists(UUID organizationId);

    boolean departmentExists(UUID organizationId, UUID departmentId);
}
