package com.orgmemory.core.knowledge;

/** One typed member named by its stable source-owned principal id. */
public record ConnectorMembershipMember(
        SourcePrincipalKind kind,
        String nativePrincipalId) {

    public ConnectorMembershipMember {
        if (kind == null) {
            throw new IllegalArgumentException("connector membership member kind is required");
        }
        if (nativePrincipalId == null || nativePrincipalId.isBlank()) {
            throw new IllegalArgumentException(
                    "connector membership member nativePrincipalId is required");
        }
        nativePrincipalId = nativePrincipalId.trim();
    }
}
