package com.orgmemory.core.organization;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Tenant-scoped display names for provenance attached to already-visible resources. */
public interface OrganizationProvenanceQuery {

    Map<UUID, String> departmentNames(
            UUID organizationId, Collection<UUID> departmentIds);

    Map<UUID, String> userNames(
            UUID organizationId, Collection<UUID> userIds);
}
