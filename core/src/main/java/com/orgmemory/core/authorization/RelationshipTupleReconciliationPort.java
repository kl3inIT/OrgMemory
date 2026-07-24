package com.orgmemory.core.authorization;

public interface RelationshipTupleReconciliationPort {

    String policyVersion();

    RelationshipTuplePage read(int pageSize, String continuationToken);

    RelationshipTuplePage read(
            RelationshipTupleFilter filter,
            int pageSize,
            String continuationToken);

    RelationshipTupleWriteResult delete(RelationshipTupleWriteRequest request);
}
