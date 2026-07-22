package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectStorageException;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectWriteRequest;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.permission.KnowledgePermissionPolicy;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SourceUploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "pptx", "txt", "md");

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
        String mediaType = normalizedMediaType(command.mediaType(), fileName);
        KnowledgeClassification classification = command.classification() == null
                ? KnowledgeClassification.CONFIDENTIAL
                : command.classification();
        DeclaredAccessScope declaredAccess = permissionPolicy.requiredScope(classification);
        if (classification == KnowledgeClassification.CONFIDENTIAL
                && targetSpace.departmentId() == null) {
            throw new IllegalArgumentException(
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
            throw new IllegalArgumentException("actor, upload metadata, and content are required");
        }
        if (command.contentLength() <= 0
                || command.contentLength() > properties.maximumUploadSize().toBytes()) {
            throw new IllegalArgumentException("file size must be within the configured upload limit");
        }
        String fileName = safeFileName(command.fileName());
        String extension = extension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("file type is not supported");
        }
        KnowledgeClassification classification = command.classification() == null
                ? KnowledgeClassification.CONFIDENTIAL
                : command.classification();
        if (command.knowledgeSpaceId() == null) {
            throw new IllegalArgumentException("Knowledge Space is required");
        }
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("file name is required");
        }
        String fileName = Path.of(value).getFileName().toString().strip();
        if (fileName.isBlank() || fileName.length() > 255) {
            throw new IllegalArgumentException("file name is invalid");
        }
        return fileName.replaceAll("\\p{Cntrl}", "_");
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizedMediaType(String claimedMediaType, String fileName) {
        if (claimedMediaType != null
                && !claimedMediaType.isBlank()
                && !"application/octet-stream".equalsIgnoreCase(claimedMediaType)) {
            return claimedMediaType.strip().toLowerCase(Locale.ROOT);
        }
        return switch (extension(fileName)) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "md" -> "text/markdown";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }
}
