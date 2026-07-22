package com.orgmemory.core.authorization;

public interface RelationshipAuthorizationSetPort {

    AuthorizedResourceSetResult listAuthorizedResources(AuthorizedResourceQuery query);

    BatchAuthorizationResult batchCheck(BatchAuthorizationQuery query);
}
