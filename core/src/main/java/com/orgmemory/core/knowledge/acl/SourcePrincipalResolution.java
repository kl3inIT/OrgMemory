package com.orgmemory.core.knowledge.acl;

import java.util.Map;
import java.util.UUID;

/**
 * The identity pass result, resolved once per batch and reused by every object reconcile:
 * each observed typed native source id mapped to its {@link SourcePrincipal} id and kind. The
 * type is part of the key because a source may legally reuse the same opaque id namespace for a
 * user and a group.
 */
public record SourcePrincipalResolution(
        Map<PrincipalKey, ResolvedPrincipal> principals) {

    public SourcePrincipalResolution {
        principals = Map.copyOf(principals);
    }

    public ResolvedPrincipal find(SourcePrincipalKind kind, String nativePrincipalId) {
        return principals.get(new PrincipalKey(kind, nativePrincipalId));
    }

    /** A resolved external principal: its registry id and observed kind. */
    public record ResolvedPrincipal(UUID id, SourcePrincipalKind kind) {
    }

    public record PrincipalKey(SourcePrincipalKind kind, String nativePrincipalId) {

        public PrincipalKey {
            if (kind == null || nativePrincipalId == null || nativePrincipalId.isBlank()) {
                throw new IllegalArgumentException("typed source principal key is required");
            }
            nativePrincipalId = nativePrincipalId.trim();
        }
    }
}
