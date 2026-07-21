package com.orgmemory.core.knowledge.storage;

public record ObjectKey(String value) {

    public ObjectKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("object key must not be blank");
        }
        value = value.trim().replace('\\', '/');
        boolean containsParentSegment = java.util.Arrays.asList(value.split("/", -1)).contains("..");
        if (value.startsWith("/") || containsParentSegment) {
            throw new IllegalArgumentException("object key must be a relative canonical path");
        }
    }
}
