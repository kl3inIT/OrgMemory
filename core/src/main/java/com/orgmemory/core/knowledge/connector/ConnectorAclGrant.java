package com.orgmemory.core.knowledge.connector;

import com.orgmemory.core.knowledge.acl.SourcePrincipalKind;

import com.orgmemory.core.permission.AccessGate;

/**
 * A single source-declared access grant for an object: a principal (user or group) and the
 * gate the source asserts for it. The connector may only translate what the source states;
 * it can never widen access beyond the payload, and only {@code ALLOW}/{@code DENY} are
 * meaningful (an unknown gate is rejected).
 *
 * @param principalKind        SOURCE_USER or SOURCE_GROUP
 * @param principalNativeId    the source-owned stable principal id
 * @param gate                 ALLOW or DENY as the source asserts
 */
public record ConnectorAclGrant(
        SourcePrincipalKind principalKind,
        String principalNativeId,
        AccessGate gate) {

    public ConnectorAclGrant {
        if (principalKind == null) {
            throw new IllegalArgumentException("connector grant principalKind is required");
        }
        if (principalNativeId == null || principalNativeId.isBlank()) {
            throw new IllegalArgumentException("connector grant principalNativeId is required");
        }
        principalNativeId = principalNativeId.trim();
        if (gate != AccessGate.ALLOW && gate != AccessGate.DENY) {
            throw new IllegalArgumentException("connector grant gate must be ALLOW or DENY");
        }
    }
}
