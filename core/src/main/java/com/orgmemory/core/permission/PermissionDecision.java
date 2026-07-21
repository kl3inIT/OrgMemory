package com.orgmemory.core.permission;

public record PermissionDecision(AccessOutcome outcome, PermissionReason reason) {

    public static PermissionDecision allow(PermissionReason reason) {
        return new PermissionDecision(AccessOutcome.ALLOW, reason);
    }

    public static PermissionDecision deny(PermissionReason reason) {
        return new PermissionDecision(AccessOutcome.DENY, reason);
    }

    public boolean allowed() {
        return outcome == AccessOutcome.ALLOW;
    }
}
