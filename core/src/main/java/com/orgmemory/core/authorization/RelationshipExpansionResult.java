package com.orgmemory.core.authorization;

import static com.orgmemory.core.shared.Texts.requireText;

import java.util.Objects;

public record RelationshipExpansionResult(
        AuthorizationQueryOutcome outcome,
        ExpansionNode root,
        String reasonCode,
        String policyVersion) {

    public RelationshipExpansionResult {
        Objects.requireNonNull(outcome, "outcome");
        reasonCode = requireText(reasonCode, "reasonCode");
        policyVersion = requireText(policyVersion, "policyVersion");
        if (outcome == AuthorizationQueryOutcome.RESOLVED && root == null) {
            throw new IllegalArgumentException("A resolved expansion must carry a root node");
        }
        if (outcome == AuthorizationQueryOutcome.INDETERMINATE && root != null) {
            throw new IllegalArgumentException("An indeterminate expansion cannot carry a root node");
        }
    }

    public static RelationshipExpansionResult resolved(ExpansionNode root, String policyVersion) {
        return new RelationshipExpansionResult(
                AuthorizationQueryOutcome.RESOLVED,
                Objects.requireNonNull(root, "root"),
                "RELATIONSHIP_EXPANSION_RESOLVED",
                policyVersion);
    }

    public static RelationshipExpansionResult indeterminate(String reasonCode, String policyVersion) {
        return new RelationshipExpansionResult(
                AuthorizationQueryOutcome.INDETERMINATE,
                null,
                reasonCode,
                policyVersion);
    }

    public boolean resolved() {
        return outcome == AuthorizationQueryOutcome.RESOLVED;
    }

}
