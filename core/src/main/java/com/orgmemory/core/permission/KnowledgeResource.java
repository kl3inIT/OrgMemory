package com.orgmemory.core.permission;

public record KnowledgeResource(
        String organizationId,
        String resourceId,
        String departmentId,
        KnowledgeClassification classification,
        DeclaredAccessScope declaredAccess) {
}
