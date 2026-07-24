package com.orgmemory.core.authorization;

import java.util.Objects;
import java.util.Set;

/**
 * The only objects an administrator may write relationships against.
 *
 * <p>OrgMemory owns organization membership and role assignment, so writing those is safe. It
 * does not own the access control of connected content: for a source object whose
 * {@code acl_authority} is {@code SOURCE}, Slack or Drive decides, and OrgMemory mirrors that
 * decision. Adding a second writer would let the two diverge, after which nobody could answer who
 * may read a document — so an administrative write may never target one.
 *
 * <p>This refuses by object type rather than by inspecting the ledger, because the check has to
 * hold for an object that does not exist yet and for one whose authority row cannot be read.
 */
public final class AdministrativeTupleScope {

    private static final Set<String> WRITABLE_OBJECT_TYPES = Set.of("organization", "role");

    private AdministrativeTupleScope() {
    }

    public static RelationshipTuple require(RelationshipTuple tuple) {
        Objects.requireNonNull(tuple, "tuple");
        String object = tuple.object();
        String type = object.substring(0, object.indexOf(':'));
        if (!WRITABLE_OBJECT_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Administrators may only write organization and role relationships, not " + type);
        }
        return tuple;
    }

    public static boolean writable(String objectType) {
        return WRITABLE_OBJECT_TYPES.contains(Objects.requireNonNull(objectType, "objectType"));
    }
}
