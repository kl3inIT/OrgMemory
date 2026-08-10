package com.orgmemory.core.assistant;

import com.orgmemory.core.knowledge.evidence.GovernedFileUpload;
import com.orgmemory.core.knowledge.evidence.GovernedFileUploadCommand;
import com.orgmemory.core.knowledge.evidence.GovernedFileUploadResult;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Multipart orchestration that creates one governed Source and then its binding. */
@Service
public class AssistantEvidenceUploadService {

    private final GovernedFileUpload sourceUploads;
    private final AssistantEvidenceService evidence;

    AssistantEvidenceUploadService(
            GovernedFileUpload sourceUploads,
            AssistantEvidenceService evidence) {
        this.sourceUploads = sourceUploads;
        this.evidence = evidence;
    }

    public AssistantEvidenceBindingView upload(
            CurrentActor actor,
            UUID conversationId,
            UUID knowledgeSpaceId,
            KnowledgeClassification classification,
            String fileName,
            long contentLength,
            InputStream content) {
        evidence.requireUploadConversation(actor, conversationId);
        GovernedFileUploadResult uploaded = sourceUploads.upload(
                new GovernedFileUploadCommand(
                        Objects.requireNonNull(actor, "actor"),
                        fileName,
                        contentLength,
                        classification,
                        knowledgeSpaceId),
                content);
        return evidence.bindUploaded(actor, conversationId, uploaded);
    }
}
