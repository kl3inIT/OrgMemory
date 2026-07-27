package com.orgmemory.core.knowledge;

import java.util.List;

/**
 * One source group's independently captured member list. Only {@link
 * ConnectorCaptureStatus#COMPLETE} evidence is eligible to become the active membership head.
 */
public record ConnectorMembershipItem(
        String groupNativePrincipalId,
        ConnectorCaptureStatus captureStatus,
        String incompleteReason,
        List<ConnectorMembershipMember> members) {

    public ConnectorMembershipItem {
        if (groupNativePrincipalId == null || groupNativePrincipalId.isBlank()) {
            throw new IllegalArgumentException(
                    "connector membership groupNativePrincipalId is required");
        }
        groupNativePrincipalId = groupNativePrincipalId.trim();
        if (captureStatus == null) {
            throw new IllegalArgumentException("connector membership captureStatus is required");
        }
        incompleteReason = normalize(incompleteReason);
        members = members == null ? List.of() : List.copyOf(members);
        if (captureStatus == ConnectorCaptureStatus.COMPLETE && incompleteReason != null) {
            throw new IllegalArgumentException(
                    "complete connector membership cannot have an incompleteReason");
        }
        if (captureStatus == ConnectorCaptureStatus.INCOMPLETE && incompleteReason == null) {
            throw new IllegalArgumentException(
                    "incomplete connector membership requires an incompleteReason");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
