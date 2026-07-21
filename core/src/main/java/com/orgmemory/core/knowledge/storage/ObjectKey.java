package com.orgmemory.core.knowledge.storage;

public record ObjectKey(String value) {

    public ObjectKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("object key must not be blank");
        }
        value = value.trim().replace('\\', '/');
        if (value.startsWith("/") || value.contains("../") || value.endsWith("/..")) {
            throw new IllegalArgumentException("object key must be a relative canonical path");
        }
    }
}
