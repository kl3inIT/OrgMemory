package com.orgmemory.core.authorization;

import java.util.List;
import java.util.Objects;

public record RelationshipTuplePage(
        List<RelationshipTuple> tuples,
        String continuationToken,
        String policyVersion,
        String reasonCode) {

    public RelationshipTuplePage {
        tuples = List.copyOf(Objects.requireNonNull(tuples, "tuples"));
        policyVersion = requireText(policyVersion, "policyVersion");
        continuationToken = blankToNull(continuationToken);
        reasonCode = blankToNull(reasonCode);
        if (reasonCode != null && !tuples.isEmpty()) {
            throw new IllegalArgumentException("An indeterminate tuple page cannot contain tuples");
        }
    }

    public static RelationshipTuplePage resolved(
            List<RelationshipTuple> tuples,
            String continuationToken,
            String policyVersion) {
        return new RelationshipTuplePage(tuples, continuationToken, policyVersion, null);
    }

    public static RelationshipTuplePage indeterminate(String reasonCode, String policyVersion) {
        return new RelationshipTuplePage(List.of(), null, policyVersion, requireText(reasonCode, "reasonCode"));
    }

    public boolean resolved() {
        return reasonCode == null;
    }

    public boolean hasNextPage() {
        return continuationToken != null;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
