package com.orgmemory.core.organization;

import java.util.Optional;
import java.util.UUID;

/** Organization-owned lookup for an active persisted Knowledge access subject. */
public interface KnowledgeAccessSubjectQuery {

    Optional<KnowledgeAccessSubject> findActive(
            UUID organizationId,
            UUID userId);
}
