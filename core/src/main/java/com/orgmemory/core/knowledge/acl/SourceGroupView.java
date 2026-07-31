package com.orgmemory.core.knowledge.acl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A source group and its active independently sealed membership snapshot. This is evidence, not
 * configuration: it cannot be edited here because enforcement reads the same active head.
 */
public record SourceGroupView(
        UUID principalId,
        String sourceSystem,
        String sourceConnectionKey,
        String nativePrincipalId,
        String observedDisplayName,
        UUID membershipSnapshotId,
        long membershipGeneration,
        Instant sealedAt,
        List<SourceGroupMemberView> members) {

    public record SourceGroupMemberView(
            UUID principalId,
            String nativePrincipalId,
            String observedDisplayName,
            String observedEmail,
            UUID appUserId,
            String appUserName) {

        public boolean mapped() {
            return appUserId != null;
        }
    }
}
