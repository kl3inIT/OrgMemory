package com.orgmemory.core.authorization;

import java.util.Map;
import java.util.Objects;

public record BatchAuthorizationResult(
        AuthorizationQueryOutcome outcome,
        Map<ResourceRef, AuthorizationDecision> decisions,
        String reasonCode,
        String policyVersion) {

    public BatchAuthorizationResult {
        Objects.requireNonNull(outcome, "outcome");
        decisions = Map.copyOf(Objects.requireNonNull(decisions, "decisions"));
        reasonCode = requireText(reasonCode, "reasonCode");
        policyVersion = requireText(policyVersion, "policyVersion");
        if (outcome == AuthorizationQueryOutcome.INDETERMINATE && !decisions.isEmpty()) {
            throw new IllegalArgumentException("Indeterminate batch results cannot contain decisions");
        }
    }

    public static BatchAuthorizationResult resolved(
            Map<ResourceRef, AuthorizationDecision> decisions,
            String policyVersion) {
        return new BatchAuthorizationResult(
                AuthorizationQueryOutcome.RESOLVED,
                decisions,
                "BATCH_AUTHORIZATION_RESOLVED",
                policyVersion);
    }

    public static BatchAuthorizationResult indeterminate(String reasonCode, String policyVersion) {
        return new BatchAuthorizationResult(
                AuthorizationQueryOutcome.INDETERMINATE,
                Map.of(),
                reasonCode,
                policyVersion);
    }

    public boolean resolved() {
        return outcome == AuthorizationQueryOutcome.RESOLVED;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
