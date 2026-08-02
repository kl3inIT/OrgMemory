package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.authorization.ResourceRef;
import java.util.UUID;

/**
 * Adapter-facing query that validates an authorization resource against its
 * canonical tenant-owned directory before policy evaluation.
 */
public interface AuthorizationResourceDirectory {

    ResourceRef require(UUID organizationId, String resourceType, UUID resourceId);
}
