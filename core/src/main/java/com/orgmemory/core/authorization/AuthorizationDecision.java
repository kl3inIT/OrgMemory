package com.orgmemory.core.authorization;

import static com.orgmemory.core.shared.Texts.requireText;

import java.util.Objects;

public record AuthorizationDecision(
        AuthorizationOutcome outcome,
        String reasonCode,
        String policyVersion) {

    public AuthorizationDecision {
        outcome = Objects.requireNonNull(outcome, "outcome");
        reasonCode = requireText(reasonCode, "reasonCode");
        policyVersion = requireText(policyVersion, "policyVersion");
    }

    public static AuthorizationDecision allow(String policyVersion) {
        return new AuthorizationDecision(AuthorizationOutcome.ALLOW, "RELATIONSHIP_ALLOWED", policyVersion);
    }

    public static AuthorizationDecision deny(String reasonCode, String policyVersion) {
        return new AuthorizationDecision(AuthorizationOutcome.DENY, reasonCode, policyVersion);
    }

    public static AuthorizationDecision indeterminate(String reasonCode, String policyVersion) {
        return new AuthorizationDecision(AuthorizationOutcome.INDETERMINATE, reasonCode, policyVersion);
    }

    public boolean allowed() {
        return outcome == AuthorizationOutcome.ALLOW;
    }

}
