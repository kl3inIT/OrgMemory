package com.orgmemory.core.authorization;

import java.util.List;
import java.util.Objects;

public record RelationshipTupleWriteRequest(List<RelationshipTuple> tuples) {

    public RelationshipTupleWriteRequest {
        tuples = List.copyOf(Objects.requireNonNull(tuples, "tuples"));
        if (tuples.isEmpty()) {
            throw new IllegalArgumentException("At least one relationship tuple is required");
        }
    }
}
