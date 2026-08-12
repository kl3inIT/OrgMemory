package com.orgmemory.core.assistant;

import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectStorageException;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectWriteRequest;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.error.BusinessNotFoundException;
import com.orgmemory.core.shared.error.BusinessErrorExposure;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantFileService {

    static final Duration RETENTION = Duration.ofDays(30);
    private final AssistantFileRepository files;
    private final ObjectStoragePort objects;
    private final Clock clock;

    AssistantFileService(AssistantFileRepository files, ObjectStoragePort objects, Clock clock) {
        this.files = files;
        this.objects = objects;
        this.clock = clock;
    }

    public AssistantFileView upload(
            CurrentActor actor,
            String originalFileName,
            long contentLength,
            InputStream content) {
        if (actor == null || content == null) {
            throw new IllegalArgumentException("actor and content are required");
        }
        String fileName = AssistantFileContentType.safeFileName(originalFileName);
        var type = AssistantFileContentType.require(fileName, contentLength);
        var verified = AssistantFileContentType.validateSignature(type, content);
        UUID fileId = UUID.randomUUID();
        ObjectKey key = new ObjectKey("organizations/" + actor.organizationId()
                + "/assistant-files/" + fileId + "/" + fileName);
        StoredObject stored = objects.put(
                new ObjectWriteRequest(
                        key,
                        contentLength,
                        type.mediaType(),
                        Map.of(
                                "organization-id", actor.organizationId().toString(),
                                "actor-user-id", actor.userId().toString(),
                                "assistant-file-id", fileId.toString())),
                verified);
        try {
            AssistantFile file = new AssistantFile(
                    fileId,
                    actor.organizationId(),
                    actor.userId(),
                    fileName,
                    type.mediaType(),
                    stored.contentLength(),
                    stored.sha256(),
                    stored.key().value(),
                    stored.etag(),
                    stored.storageVersion(),
                    clock.instant().plus(RETENTION));
            return files.saveAndFlush(file).view();
        } catch (RuntimeException persistenceFailure) {
            try {
                objects.delete(key);
            } catch (ObjectStorageException cleanupFailure) {
                persistenceFailure.addSuppressed(cleanupFailure);
            }
            throw persistenceFailure;
        }
    }

    @Transactional(readOnly = true)
    public AssistantFilePage recent(CurrentActor actor, int requestedPage, int requestedLimit) {
        int page = Math.max(0, requestedPage);
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        var result = files.recent(
                actor.organizationId(), actor.userId(), clock.instant(), PageRequest.of(page, limit));
        return new AssistantFilePage(
                result.getContent().stream().map(AssistantFile::view).toList(),
                page,
                result.hasNext());
    }

    @Transactional(readOnly = true)
    public AssistantFileView get(CurrentActor actor, UUID fileId) {
        return requireUsableOwner(actor, fileId, false).view();
    }

    @Transactional
    public void delete(CurrentActor actor, UUID fileId) {
        requireOwner(actor, fileId).markDeleting(clock.instant());
    }

    @Transactional(readOnly = true)
    public AssistantFileContent open(CurrentActor actor, UUID fileId) {
        AssistantFile file = requireUsableOwner(actor, fileId, false);
        var object = objects.open(new ObjectKey(file.objectKey()));
        var type = AssistantFileContentType.require(file.fileName(), file.contentLength());
        return new AssistantFileContent(
                object.stream(),
                file.fileName(),
                type.browserSafeMediaType(),
                file.contentLength(),
                type.inlinePreviewAllowed());
    }

    AssistantFile requireUsableOwner(CurrentActor actor, UUID fileId, boolean readyOnly) {
        AssistantFile file = requireOwner(actor, fileId);
        Instant now = clock.instant();
        boolean denied = file.expiresAt().compareTo(now) <= 0
                || file.status() == AssistantFileStatus.DELETING
                || file.status() == AssistantFileStatus.DELETED
                || file.status() == AssistantFileStatus.EXPIRED
                || (readyOnly && file.status() != AssistantFileStatus.READY);
        if (denied) {
            throw notFound();
        }
        return file;
    }

    AssistantFile requireOwner(CurrentActor actor, UUID fileId) {
        return files.findByIdAndOrganizationIdAndActorUserId(
                        fileId, actor.organizationId(), actor.userId())
                .orElseThrow(AssistantFileService::notFound);
    }

    public static BusinessNotFoundException notFound() {
        return new BusinessNotFoundException(
                "assistant.file-not-found",
                "Assistant file not found",
                BusinessErrorExposure.OPAQUE_RESOURCE);
    }
}
