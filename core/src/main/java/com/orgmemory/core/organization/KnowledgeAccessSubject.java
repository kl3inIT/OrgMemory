package com.orgmemory.core.organization;

import java.util.Objects;
import java.util.UUID;

/** Canonical persisted identity facts required by permission-aware Knowledge reads. */
public record KnowledgeAccessSubject(
        UUID userId,
        UUID organizationId,
        UUID departmentId,
        boolean executive) {

    public KnowledgeAccessSubject {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(organizationId, "organizationId");
    }
}
