package com.orgmemory.core.permission.dataset;

import java.util.Locale;

public enum DatasetPermissionRule {
    ALLOW,
    OWN_DEPARTMENT,
    DENY;

    public static DatasetPermissionRule fromDatasetValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
    }
}
