package com.orgmemory.core.authorization;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record ResourceRef(UUID organizationId, String type, String id) {

    private static final Pattern TYPE_FORMAT = Pattern.compile("[a-z][a-z0-9_]*");

    public ResourceRef {
        organizationId = Objects.requireNonNull(organizationId, "organizationId");
        type = normalizeType(type);
        id = normalizeId(id);
    }

    public static ResourceRef of(UUID organizationId, String type, UUID id) {
        return new ResourceRef(organizationId, type, Objects.requireNonNull(id, "id").toString());
    }

    public String openFgaObject() {
        return type + ":" + id;
    }

    private static String normalizeType(String value) {
        String normalized = Objects.requireNonNull(value, "type").trim();
        if (!TYPE_FORMAT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Resource type must be a singular lowercase OpenFGA type");
        }
        return normalized;
    }

    private static String normalizeId(String value) {
        String normalized = Objects.requireNonNull(value, "id").trim();
        if (normalized.isEmpty() || normalized.indexOf(':') >= 0 || normalized.indexOf('#') >= 0) {
            throw new IllegalArgumentException("Resource id must be non-empty and must not contain ':' or '#'");
        }
        return normalized;
    }
}
