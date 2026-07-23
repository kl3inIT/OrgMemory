package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.UUID;

/**
 * An observed external principal as an administrator needs to see it: what the
 * source reported, and whether anything verified turns it into an internal user.
 */
public record SourcePrincipalView(
        UUID id,
        String sourceSystem,
        String sourceConnectionKey,
        String externalKey,
        SourcePrincipalKind kind,
        String observedEmail,
        String observedDisplayName,
        boolean ssoVerified,
        Instant lastSeenAt,
        SourcePrincipalMappingView mapping) {

    public boolean mapped() {
        return mapping != null;
    }
}
