package com.orgmemory.core.permission;

import java.util.Locale;

public enum KnowledgeRole {
    EMPLOYEE,
    MANAGER,
    DIRECTOR,
    EXECUTIVE;

    public static KnowledgeRole fromDatasetValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
