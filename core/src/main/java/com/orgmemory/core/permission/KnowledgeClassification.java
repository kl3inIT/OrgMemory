package com.orgmemory.core.permission;

import java.util.Locale;

public enum KnowledgeClassification {
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED;

    public static KnowledgeClassification fromDatasetValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
