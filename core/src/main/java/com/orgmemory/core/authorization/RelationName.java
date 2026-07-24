package com.orgmemory.core.authorization;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Any relation in the authorization model, including the assignable nouns.
 *
 * <p>{@link PermissionKey} deliberately admits only {@code can_*} because that is what may be
 * enforced. Explaining a decision has to walk through the nouns those permissions are composed
 * from — {@code viewer}, {@code manager}, {@code assignee} — so it needs a name that is not a
 * permission. Keeping them as separate types is what stops a noun reaching an enforcement call.
 */
public record RelationName(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_]*");

    public RelationName {
        value = Objects.requireNonNull(value, "value").trim();
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Relation names must be lowercase OpenFGA relations");
        }
    }

    public static RelationName of(String value) {
        return new RelationName(value);
    }

    public static RelationName of(PermissionKey permission) {
        return new RelationName(Objects.requireNonNull(permission, "permission").value());
    }
}
