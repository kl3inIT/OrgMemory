package com.orgmemory.core.authorization;

public interface RelationshipAuthorizationPort {

    AuthorizationDecision check(RelationshipAuthorizationQuery query);
}
