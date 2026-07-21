package com.orgmemory.core.permission;

public record KnowledgeAccessRequest(
        KnowledgeSubject subject,
        KnowledgeResource resource,
        AccessGate sourcePermission,
        AccessGate orgMemoryPermission) {
}
