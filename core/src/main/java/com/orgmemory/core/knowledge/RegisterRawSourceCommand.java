package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RegisterRawSourceCommand(
        UUID organizationId,
        UUID departmentId,
        String sourceSystem,
        String sourceConnectionKey,
        String externalObjectId,
        String sourceVersion,
        String objectType,
        String title,
        String rawContent,
        String sourceUri,
        Instant sourceModifiedAt,
        KnowledgeClassification classification,
        DeclaredAccessScope declaredAccess,
        AclCaptureStatus aclCaptureStatus,
        AccessGate defaultGate,
        Instant aclValidUntil,
        List<SourceAclEntryCommand> aclEntries,
        UUID expectedCurrentAclSnapshotId) {

    public RegisterRawSourceCommand {
        aclEntries = aclEntries == null ? List.of() : List.copyOf(aclEntries);
    }

    public RegisterRawSourceCommand(
            UUID organizationId,
            UUID departmentId,
            String sourceSystem,
            String sourceConnectionKey,
            String externalObjectId,
            String sourceVersion,
            String objectType,
            String title,
            String rawContent,
            String sourceUri,
            Instant sourceModifiedAt,
            KnowledgeClassification classification,
            DeclaredAccessScope declaredAccess,
            AclCaptureStatus aclCaptureStatus,
            AccessGate defaultGate,
            Instant aclValidUntil,
            List<SourceAclEntryCommand> aclEntries) {
        this(
                organizationId,
                departmentId,
                sourceSystem,
                sourceConnectionKey,
                externalObjectId,
                sourceVersion,
                objectType,
                title,
                rawContent,
                sourceUri,
                sourceModifiedAt,
                classification,
                declaredAccess,
                aclCaptureStatus,
                defaultGate,
                aclValidUntil,
                aclEntries,
                null);
    }
}
