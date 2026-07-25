package com.orgmemory.graphrag.storage;

import java.util.Objects;
import java.util.UUID;

public record ProjectionNamespace(
        UUID organizationId,
        String workspace,
        String collection) {

    public ProjectionNamespace {
        Objects.requireNonNull(organizationId, "organizationId");
        workspace = requireText(workspace, "workspace");
        collection = requireText(collection, "collection");
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
