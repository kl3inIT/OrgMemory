package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrganizationProvenanceQuery;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceUploadRegistrationService {

    private final SourceObjectRepository sources;
    private final EvidenceBlobRepository blobs;
    private final SourceRevisionRepository revisions;
    private final SourceIngestionJobRepository jobs;
    private final SourceIngestionProperties properties;
    private final OrganizationProvenanceQuery provenance;

    SourceUploadRegistrationService(
            SourceObjectRepository sources,
            EvidenceBlobRepository blobs,
            SourceRevisionRepository revisions,
            SourceIngestionJobRepository jobs,
            SourceIngestionProperties properties,
            OrganizationProvenanceQuery provenance) {
        this.sources = sources;
        this.blobs = blobs;
        this.revisions = revisions;
        this.jobs = jobs;
        this.properties = properties;
        this.provenance = provenance;
    }

    @Transactional
    SourceUploadResult register(
            UUID sourceId,
            UUID revisionId,
            UUID blobId,
            CurrentActor actor,
            SourceKnowledgeSpaceRef targetSpace,
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
        SourceRevision revision = revisions.saveAndFlush(
                new SourceRevision(revisionId, source, blob, fileName, 1));
        source.stageRevision(revision.getId());
        sources.save(source);
        jobs.save(new SourceIngestionJob(
                actor.organizationId(), revision.getId(), properties.maximumAttempts(), Instant.now()));
        String owningDepartmentName = targetSpace.departmentId() == null
                ? null
                : provenance.departmentNames(
                                actor.organizationId(), java.util.List.of(targetSpace.departmentId()))
                        .get(targetSpace.departmentId());
        return new SourceUploadResult(
                SourceQueryService.summary(
                        source,
                        revision,
                        null,
                        targetSpace,
                        owningDepartmentName,
                        actor.name()),
                revision.getId(),
                targetSpace.id());
    }
}
