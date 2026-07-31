package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.knowledge.space.KnowledgeSpaceService;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceTarget;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectStorageException;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectWriteRequest;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.permission.KnowledgePermissionPolicy;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SourceUploadService {

    private final ObjectStoragePort objects;
    private final SourceUploadRegistrationService registrations;
    private final KnowledgePermissionPolicy permissionPolicy;
    private final SourceIngestionProperties properties;
    private final KnowledgeSpaceService knowledgeSpaces;

    SourceUploadService(
            ObjectStoragePort objects,
            SourceUploadRegistrationService registrations,
            KnowledgePermissionPolicy permissionPolicy,
            SourceIngestionProperties properties,
            KnowledgeSpaceService knowledgeSpaces) {
        this.objects = objects;
        this.registrations = registrations;
        this.permissionPolicy = permissionPolicy;
        this.properties = properties;
        this.knowledgeSpaces = knowledgeSpaces;
    }

    public SourceSummary upload(CreateUploadSourceCommand command, InputStream content) {
        validate(command, content);
        CurrentActor actor = command.actor();
        KnowledgeSpaceTarget targetSpace = knowledgeSpaces.requireUploadTarget(
                actor, command.knowledgeSpaceId());
        String fileName = safeFileName(command.fileName());
        String mediaType = requiredUploadType(fileName).canonicalMediaType();
        KnowledgeClassification classification = command.classification() == null
                ? KnowledgeClassification.CONFIDENTIAL
                : command.classification();
        DeclaredAccessScope declaredAccess = permissionPolicy.requiredScope(classification);
        if (classification == KnowledgeClassification.CONFIDENTIAL
                && targetSpace.departmentId() == null) {
            throw invalidUpload(
                    "source.upload-space-invalid",
                    "confidential upload requires a department Knowledge Space");
        }
        UUID sourceId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        UUID blobId = UUID.randomUUID();
        ObjectKey objectKey = new ObjectKey("organizations/" + actor.organizationId()
                + "/sources/" + sourceId
                + "/revisions/" + revisionId
                + "/" + fileName);
        StoredObject stored = objects.put(
                new ObjectWriteRequest(
                        objectKey,
                        command.contentLength(),
                        mediaType,
                        Map.of(
                                "organization-id", actor.organizationId().toString(),
                                "source-object-id", sourceId.toString(),
                                "source-revision-id", revisionId.toString())),
                content);
        try {
            return registrations.register(
                    sourceId,
                    revisionId,
                    blobId,
                    actor,
                    targetSpace,
                    fileName,
                    classification,
                    declaredAccess,
                    stored);
        } catch (RuntimeException registrationFailure) {
            try {
                objects.delete(objectKey);
            } catch (ObjectStorageException cleanupFailure) {
                registrationFailure.addSuppressed(cleanupFailure);
            }
            throw registrationFailure;
        }
    }

    private void validate(CreateUploadSourceCommand command, InputStream content) {
        if (command == null || command.actor() == null || content == null) {
            throw invalidUpload(
                    "source.upload-request-invalid",
                    "actor, upload metadata, and content are required");
        }
        if (command.contentLength() <= 0
                || command.contentLength() > properties.maximumUploadSize().toBytes()) {
            throw invalidUpload(
                    "source.upload-size-invalid",
                    "file size must be within the configured upload limit");
        }
        String fileName = safeFileName(command.fileName());
        requiredUploadType(fileName);
        if (command.knowledgeSpaceId() == null) {
            throw invalidUpload(
                    "source.upload-space-required",
                    "Knowledge Space is required");
        }
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) {
            throw invalidUpload(
                    "source.upload-filename-invalid",
                    "file name is required");
        }
        String fileName;
        try {
            Path name = Path.of(value).getFileName();
            fileName = name == null ? "" : name.toString().strip();
        } catch (InvalidPathException invalidPath) {
            throw new BusinessValidationException(
                    "source.upload-filename-invalid",
                    "file name is invalid",
                    invalidPath);
        }
        if (fileName.isBlank() || fileName.length() > 255) {
            throw invalidUpload(
                    "source.upload-filename-invalid",
                    "file name is invalid");
        }
        return fileName.replaceAll("\\p{Cntrl}", "_");
    }

    private static KnowledgeContentType requiredUploadType(String fileName) {
        return KnowledgeContentType.fromFileName(fileName)
                .filter(KnowledgeContentType::uploadAllowed)
                .orElseThrow(() ->
                        invalidUpload(
                                "source.upload-type-unsupported",
                                "file type is not supported"));
    }

    private static BusinessValidationException invalidUpload(
            String code,
            String message) {
        return new BusinessValidationException(code, message);
    }
}
