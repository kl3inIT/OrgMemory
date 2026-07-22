package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SourceUploadRegistrationService {

    private final SourceObjectRepository sources;
    private final EvidenceBlobRepository blobs;
    private final SourceRevisionRepository revisions;
    private final SourceIngestionJobRepository jobs;
    private final SourceIngestionProperties properties;

    SourceUploadRegistrationService(
            SourceObjectRepository sources,
            EvidenceBlobRepository blobs,
            SourceRevisionRepository revisions,
            SourceIngestionJobRepository jobs,
            SourceIngestionProperties properties) {
        this.sources = sources;
        this.blobs = blobs;
        this.revisions = revisions;
        this.jobs = jobs;
        this.properties = properties;
    }

    @Transactional
    SourceSummary register(
            UUID sourceId,
            UUID revisionId,
            UUID blobId,
            CurrentActor actor,
            KnowledgeSpaceTarget targetSpace,
            String fileName,
            KnowledgeClassification classification,
            DeclaredAccessScope declaredAccess,
            StoredObject stored) {
        SourceObject source = sources.saveAndFlush(new SourceObject(
                sourceId,
                actor.organizationId(),
                targetSpace.id(),
                targetSpace.departmentId(),
                actor.userId(),
                fileName,
                classification,
                declaredAccess));
        EvidenceBlob blob = blobs.saveAndFlush(new EvidenceBlob(blobId, actor.organizationId(), stored));
        SourceRevision revision = revisions.saveAndFlush(new SourceRevision(revisionId, source, blob, fileName));
        source.useRevision(revision.getId());
        sources.save(source);
        jobs.save(new SourceIngestionJob(
                actor.organizationId(), revision.getId(), properties.maximumAttempts(), Instant.now()));
        return SourceQueryService.summary(source, revision, null);
    }
}
