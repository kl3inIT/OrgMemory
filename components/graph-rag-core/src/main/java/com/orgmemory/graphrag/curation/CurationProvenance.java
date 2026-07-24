package com.orgmemory.graphrag.curation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Audit provenance attached to every append-only graph curation record. */
public record CurationProvenance(
        UUID actorUserId,
        String authorizationModelId,
        long aclGeneration,
        Instant curatedAt,
        String reason) {

    public CurationProvenance {
        Objects.requireNonNull(actorUserId, "actorUserId");
        authorizationModelId =
                requireText(authorizationModelId, "authorizationModelId");
        Objects.requireNonNull(curatedAt, "curatedAt");
        reason = requireText(reason, "reason");
        if (aclGeneration < 0) {
            throw new IllegalArgumentException("aclGeneration must be non-negative");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
