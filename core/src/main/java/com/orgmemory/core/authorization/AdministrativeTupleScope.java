package com.orgmemory.core.authorization;

import java.util.Objects;
import java.util.Set;

/**
 * The only objects an administrator may write relationships against.
 *
 * <p>The test is whether OrgMemory is the sole author of an object's access. It is for
 * organization membership and a Knowledge Space: no connector creates one, no
 * crawl updates one, and {@code knowledge_spaces} carries no {@code acl_authority} column because
 * there is no external authority to defer to.
 *
 * <p>It is not for a {@code knowledge_asset}. An asset descends from a source object whose
 * {@code acl_authority} may be {@code SOURCE}, in which case Slack or Drive decides and OrgMemory
 * mirrors that decision. Adding a second writer would let the two diverge, after which nobody
 * could answer who may read a document — so an administrative write may never target one.
 *
 * <p>Granting a space widens nothing on its own. A space grant satisfies one gate of the
 * retrieval chain; the mirrored source ACL still caps every read behind it.
 *
 * <p>This refuses by object type rather than by inspecting the ledger, because the check has to
 * hold for an object that does not exist yet and for one whose authority row cannot be read.
 */
public final class AdministrativeTupleScope {

    private static final Set<String> WRITABLE_OBJECT_TYPES =
            Set.of("organization", "knowledge_space");

    private AdministrativeTupleScope() {
    }

    public static RelationshipTuple require(RelationshipTuple tuple) {
        Objects.requireNonNull(tuple, "tuple");
        String object = tuple.object();
        String type = object.substring(0, object.indexOf(':'));
        if (!WRITABLE_OBJECT_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Administrators may only write organization and Knowledge Space "
                            + "relationships, not " + type);
        }
        return tuple;
    }

    public static boolean writable(String objectType) {
        return WRITABLE_OBJECT_TYPES.contains(Objects.requireNonNull(objectType, "objectType"));
    }
}
