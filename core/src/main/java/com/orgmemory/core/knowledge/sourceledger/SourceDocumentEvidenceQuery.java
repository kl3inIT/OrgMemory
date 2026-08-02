package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.knowledge.storage.ObjectKey;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only Source Ledger boundary for one tenant-scoped current document. */
@Service
@Transactional(readOnly = true)
public class SourceDocumentEvidenceQuery {

    private final SourceRevisionRepository revisions;
    private final EvidenceBlobRepository blobs;

    SourceDocumentEvidenceQuery(
            SourceRevisionRepository revisions,
            EvidenceBlobRepository blobs) {
        this.revisions = revisions;
        this.blobs = blobs;
    }

    public Optional<SourceDocumentEvidence> findAvailable(
            UUID organizationId, UUID sourceObjectId) {
        UUID tenantId = Objects.requireNonNull(organizationId, "organizationId");
        UUID sourceId = Objects.requireNonNull(sourceObjectId, "sourceObjectId");
        SourceRevision revision = revisions
                .findCurrentReadyBySourceObjectIdAndOrganizationId(sourceId, tenantId)
                .filter(candidate -> candidate.getKnowledgeAssetId() != null)
                .orElse(null);
        if (revision == null) {
            return Optional.empty();
        }
        EvidenceBlob blob = blobs
                .findByIdAndOrganizationId(revision.getEvidenceBlobId(), tenantId)
                .filter(candidate -> candidate.getScanStatus()
                        == EvidenceScanStatus.BASIC_VALIDATED)
                .orElse(null);
        if (blob == null) {
            return Optional.empty();
        }
        return Optional.of(new SourceDocumentEvidence(
                revision.getId(),
                revision.getKnowledgeAssetId(),
                revision.getEmbeddingProfileId(),
                new SourceCitationEvidence(
                        revision.getFileName(),
                        revision.getMediaType(),
                        revision.getContentLength(),
                        revision.getContentSha256(),
                        new ObjectKey(blob.getObjectKey()),
                        blob.getContentLength(),
                        blob.getContentSha256())));
    }
}
