package com.orgmemory.core.knowledge.acl;

/** One typed member named by its stable source-owned principal id. */
public record SourceGroupMembershipMemberCommand(
        SourcePrincipalKind kind,
        String nativePrincipalId) {

    public SourceGroupMembershipMemberCommand {
        if (kind == null) {
            throw new IllegalArgumentException("source membership member kind is required");
        }
        if (nativePrincipalId == null || nativePrincipalId.isBlank()) {
            throw new IllegalArgumentException(
                    "source membership member nativePrincipalId is required");
        }
        nativePrincipalId = nativePrincipalId.trim();
    }
}
