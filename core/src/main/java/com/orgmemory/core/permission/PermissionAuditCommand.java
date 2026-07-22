package com.orgmemory.core.permission;

import java.util.UUID;

public record PermissionAuditCommand(
        UUID organizationId,
        UUID actorUserId,
        String operation,
        String resourceType,
        String resourceId,
        PermissionAuditDecision decision,
        String reasonCode,
        String policyVersion,
        String requestId,
        String queryText,
        UUID ingestionAclSnapshotId,
        UUID currentAclSnapshotId,
        String authorizationModelId,
        UUID sourceRevisionId,
        UUID knowledgeChunkId,
        UUID embeddingProfileId,
        Long projectionGeneration) {

    public PermissionAuditCommand(
            UUID organizationId,
            UUID actorUserId,
            String operation,
            String resourceType,
            String resourceId,
            PermissionAuditDecision decision,
            String reasonCode,
            String policyVersion,
            String requestId,
            String queryText) {
        this(
                organizationId,
                actorUserId,
                operation,
                resourceType,
                resourceId,
                decision,
                reasonCode,
                policyVersion,
                requestId,
                queryText,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
