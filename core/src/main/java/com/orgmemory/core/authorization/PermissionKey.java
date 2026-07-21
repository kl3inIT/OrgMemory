package com.orgmemory.core.authorization;

import java.util.Objects;
import java.util.regex.Pattern;

public record PermissionKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("can_[a-z0-9]+(?:_[a-z0-9]+)*");

    public PermissionKey {
        value = Objects.requireNonNull(value, "value").trim();
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Permission keys must use the OpenFGA can_<action> format");
        }
    }

    public static PermissionKey of(String value) {
        return new PermissionKey(value);
    }
}
