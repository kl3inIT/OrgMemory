package com.orgmemory.core.knowledge.acl;

import java.util.List;

/** One independently captured source-group membership snapshot. */
public record SourceGroupMembershipCommand(
        String groupNativePrincipalId,
        SourceMembershipCaptureStatus captureStatus,
        String incompleteReason,
        List<SourceGroupMembershipMemberCommand> members) {

    public SourceGroupMembershipCommand {
        if (groupNativePrincipalId == null || groupNativePrincipalId.isBlank()) {
            throw new IllegalArgumentException(
                    "source membership groupNativePrincipalId is required");
        }
        groupNativePrincipalId = groupNativePrincipalId.trim();
        if (captureStatus == null) {
            throw new IllegalArgumentException("source membership captureStatus is required");
        }
        incompleteReason = normalize(incompleteReason);
        members = members == null ? List.of() : List.copyOf(members);
        if (captureStatus == SourceMembershipCaptureStatus.COMPLETE && incompleteReason != null) {
            throw new IllegalArgumentException(
                    "complete source membership cannot have an incompleteReason");
        }
        if (captureStatus == SourceMembershipCaptureStatus.INCOMPLETE && incompleteReason == null) {
            throw new IllegalArgumentException(
                    "incomplete source membership requires an incompleteReason");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
