package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A source group and the membership sealed with its most recent ACL generation.
 * This is evidence, not configuration: it cannot be edited here because it was
 * fixed at seal time and is what enforcement reads.
 */
public record SourceGroupView(
        UUID principalId,
        String sourceSystem,
        String sourceConnectionKey,
        String externalKey,
        String observedDisplayName,
        UUID sourceAclSnapshotId,
        long aclGeneration,
        Instant sealedAt,
        List<SourceGroupMemberView> members) {

    public record SourceGroupMemberView(
            UUID principalId,
            String externalKey,
            String observedDisplayName,
            String observedEmail,
            UUID appUserId,
            String appUserName) {

        public boolean mapped() {
            return appUserId != null;
        }
    }
}
