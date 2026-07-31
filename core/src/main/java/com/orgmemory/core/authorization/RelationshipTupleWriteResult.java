package com.orgmemory.core.authorization;

import static com.orgmemory.core.shared.Texts.requireText;

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

}
