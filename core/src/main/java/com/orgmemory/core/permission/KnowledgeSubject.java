package com.orgmemory.core.permission;

public record KnowledgeSubject(
        String organizationId,
        String subjectId,
        String departmentId,
        KnowledgeRole role,
        boolean active) {
}
