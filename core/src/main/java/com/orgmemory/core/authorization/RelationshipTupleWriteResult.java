package com.orgmemory.core.authorization;

import java.util.Objects;

public record RelationshipTupleWriteResult(
        RelationshipTupleWriteOutcome outcome,
        String reasonCode,
        String policyVersion) {

    public RelationshipTupleWriteResult {
        Objects.requireNonNull(outcome, "outcome");
        reasonCode = requireText(reasonCode, "reasonCode");
        policyVersion = requireText(policyVersion, "policyVersion");
    }

    public static RelationshipTupleWriteResult applied(String policyVersion) {
        return new RelationshipTupleWriteResult(
                RelationshipTupleWriteOutcome.APPLIED,
                "RELATIONSHIPS_APPLIED",
                policyVersion);
    }

    public static RelationshipTupleWriteResult indeterminate(String reasonCode, String policyVersion) {
        return new RelationshipTupleWriteResult(
                RelationshipTupleWriteOutcome.INDETERMINATE,
                reasonCode,
                policyVersion);
    }

    public boolean applied() {
        return outcome == RelationshipTupleWriteOutcome.APPLIED;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
