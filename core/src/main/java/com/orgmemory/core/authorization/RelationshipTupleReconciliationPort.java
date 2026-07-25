package com.orgmemory.core.authorization;

public interface RelationshipTupleReconciliationPort {

    String policyVersion();

    RelationshipTuplePage read(int pageSize, String continuationToken);

    RelationshipTuplePage read(
            RelationshipTupleFilter filter,
            int pageSize,
            String continuationToken);

    /**
     * The tuples stored against one object.
     *
     * <p>{@link #read} exists because OpenFGA has no "list every object of a type" call, so a
     * listing that spans objects has to page the store. Asking about a single object does not:
     * the store filters it, and the answer is exact rather than capped.
     *
     * @param object an object reference such as {@code knowledge_space:<id>}
     */
    RelationshipTuplePage readObject(String object, int pageSize, String continuationToken);

    RelationshipTupleWriteResult delete(RelationshipTupleWriteRequest request);
}
