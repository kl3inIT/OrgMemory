package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.UUID;

/**
 * One external identity as reported by a source crawl, plus any IdP-join evidence
 * available for it. Observation is not authorization; it only feeds the principal
 * registry and the matcher.
 */
record SourceIdentityObservation(
        UUID organizationId,
        String sourceSystem,
        String sourceConnectionKey,
        String externalKey,
        SourcePrincipalKind kind,
        String observedEmail,
        String observedDisplayName,
        boolean ssoVerified,
        String idpIssuer,
        String idpSubject,
        Instant observedAt) {
}
