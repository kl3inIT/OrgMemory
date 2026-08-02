package com.orgmemory.core.knowledge.sourceledger;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Source Ledger-owned lifecycle commands for canonical source objects. */
@Service
public class SourceLifecycleService {

    private final SourceObjectRepository sources;
    private final SourceRevisionRepository revisions;

    SourceLifecycleService(
            SourceObjectRepository sources,
            SourceRevisionRepository revisions) {
        this.sources = sources;
        this.revisions = revisions;
    }

    @Transactional(readOnly = true)
    public ReadyManualUploadRef requireReadyManualUpload(UUID organizationId, UUID sourceId) {
        SourceObject source = manualUpload(organizationId, sourceId);
        SourceRevision revision = revisions
                .findByIdAndOrganizationId(source.getCurrentRevisionId(), organizationId)
                .filter(candidate -> candidate.getStatus() == SourceRevisionStatus.READY)
                .filter(candidate -> candidate.getKnowledgeAssetId() != null)
                .filter(candidate -> candidate.getKnowledgeAssetVersionId() != null)
                .orElseThrow(com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException::new);
        return new ReadyManualUploadRef(
                source.getId(),
                revision.getKnowledgeAssetId(),
                revision.getKnowledgeAssetVersionId(),
                source.getStatus() == SourceObjectStatus.ARCHIVED);
    }

    @Transactional
    public void archiveReadyManualUpload(
            UUID organizationId, UUID sourceId, UUID expectedAssetId) {
        SourceObject source = manualUpload(organizationId, sourceId);
        SourceRevision revision = revisions
                .findByIdAndOrganizationId(source.getCurrentRevisionId(), organizationId)
                .filter(candidate -> candidate.getStatus() == SourceRevisionStatus.READY)
                .filter(candidate -> expectedAssetId.equals(candidate.getKnowledgeAssetId()))
                .orElseThrow(com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException::new);
        if (source.getStatus() == SourceObjectStatus.ACTIVE) {
            source.archive();
            sources.saveAndFlush(source);
        }
    }

    private SourceObject manualUpload(UUID organizationId, UUID sourceId) {
        return sources.findByIdAndOrganizationId(
                        Objects.requireNonNull(sourceId, "sourceId"),
                        Objects.requireNonNull(organizationId, "organizationId"))
                .filter(source -> source.getStatus() == SourceObjectStatus.ACTIVE
                        || source.getStatus() == SourceObjectStatus.ARCHIVED)
                .filter(source -> SourceObject.NATIVE_UPLOAD_SYSTEM.equals(source.getSourceSystem()))
                .filter(source -> source.getCurrentRevisionId() != null)
                .orElseThrow(com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException::new);
    }

    /** Retires an active source while retaining its evidence and history. */
    @Transactional
    public boolean retire(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            String externalObjectId) {
        var existing = sources
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        Objects.requireNonNull(organizationId, "organizationId"),
                        Objects.requireNonNull(sourceSystem, "sourceSystem"),
                        Objects.requireNonNull(sourceConnectionKey, "sourceConnectionKey"),
                        Objects.requireNonNull(externalObjectId, "externalObjectId"));
        if (existing.isEmpty() || existing.get().getStatus() != SourceObjectStatus.ACTIVE) {
            return false;
        }
        SourceObject source = existing.get();
        source.archive();
        sources.saveAndFlush(source);
        return true;
    }
}
