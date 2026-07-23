package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.UUID;

/**
 * The resolved membership of one source group, to be sealed with an ACL generation. Both
 * ids are {@link SourcePrincipal} identifiers the connector has already observed; the
 * connector translates external keys to these before handing membership to the seal.
 */
record SealedGroupMembership(UUID groupPrincipalId, List<UUID> memberPrincipalIds) {

    SealedGroupMembership {
        if (groupPrincipalId == null) {
            throw new IllegalArgumentException("groupPrincipalId is required");
        }
        memberPrincipalIds = memberPrincipalIds == null ? List.of() : List.copyOf(memberPrincipalIds);
    }
}
