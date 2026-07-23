package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.UUID;

/**
 * The active link between an observed principal and an internal user, with the
 * tier that established it. Absent means unmapped, which retrieval reads as deny.
 */
public record SourcePrincipalMappingView(
        UUID id,
        UUID appUserId,
        String appUserName,
        String appUserEmail,
        SourcePrincipalMappingMethod method,
        SourcePrincipalMappingStatus status,
        String evidence,
        Instant verifiedAt) {
}
