package com.orgmemory.core.knowledge;

import java.util.Map;
import java.util.UUID;

/**
 * The identity pass result, resolved once per batch and reused by every object reconcile:
 * each observed typed native source id mapped to its {@link SourcePrincipal} id and kind. The
 * type is part of the key because a source may legally reuse the same opaque id namespace for a
 * user and a group.
 */
record ConnectorIdentityResolution(
        Map<PrincipalKey, ResolvedPrincipal> principals) {

    ConnectorIdentityResolution {
        principals = Map.copyOf(principals);
    }

    ResolvedPrincipal find(SourcePrincipalKind kind, String nativePrincipalId) {
        return principals.get(new PrincipalKey(kind, nativePrincipalId));
    }

    /** A resolved external principal: its registry id and observed kind. */
    record ResolvedPrincipal(UUID id, SourcePrincipalKind kind) {
    }

    record PrincipalKey(SourcePrincipalKind kind, String nativePrincipalId) {

        PrincipalKey {
            if (kind == null || nativePrincipalId == null || nativePrincipalId.isBlank()) {
                throw new IllegalArgumentException("typed connector principal key is required");
            }
            nativePrincipalId = nativePrincipalId.trim();
        }
    }
}
