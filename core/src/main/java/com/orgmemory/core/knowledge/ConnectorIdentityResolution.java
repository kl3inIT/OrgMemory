package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The identity pass result, resolved once per batch and reused by every object reconcile:
 * each observed external key mapped to its {@link SourcePrincipal} id and kind, plus each
 * group's resolved member principal ids. Content reconciliation reads this to translate the
 * permission payload's external keys into sealed ACL entries and membership.
 */
record ConnectorIdentityResolution(
        Map<String, ResolvedPrincipal> principalsByKey,
        Map<String, List<UUID>> memberPrincipalIdsByGroupKey) {

    ConnectorIdentityResolution {
        principalsByKey = Map.copyOf(principalsByKey);
        memberPrincipalIdsByGroupKey = Map.copyOf(memberPrincipalIdsByGroupKey);
    }

    /** A resolved external principal: its registry id and observed kind. */
    record ResolvedPrincipal(UUID id, SourcePrincipalKind kind) {
    }
}
