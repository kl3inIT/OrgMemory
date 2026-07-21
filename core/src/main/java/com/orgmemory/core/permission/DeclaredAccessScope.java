package com.orgmemory.core.permission;

import java.util.Locale;

public enum DeclaredAccessScope {
    ALL,
    ALL_EMPLOYEES,
    OWN_DEPARTMENT,
    EXECUTIVE_ONLY;

    public static DeclaredAccessScope fromDatasetValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
    }
}
