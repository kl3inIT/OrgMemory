package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.knowledge.storage.ObjectKey;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only Source Ledger boundary for tenant-scoped citation evidence. */
@Service
@Transactional(readOnly = true)
public class SourceCitationEvidenceQuery {

    private final SourceRevisionRepository revisions;
    private final EvidenceBlobRepository blobs;

    SourceCitationEvidenceQuery(
            SourceRevisionRepository revisions,
            EvidenceBlobRepository blobs) {
        this.revisions = revisions;
        this.blobs = blobs;
    }

    public SourceCitationEvidenceResult findAvailable(
            UUID organizationId,
            UUID sourceRevisionId,
            UUID expectedKnowledgeAssetId) {
        UUID tenantId = Objects.requireNonNull(
                organizationId, "organizationId");
        UUID revisionId = Objects.requireNonNull(
                sourceRevisionId, "sourceRevisionId");
        UUID assetId = Objects.requireNonNull(
                expectedKnowledgeAssetId, "expectedKnowledgeAssetId");
        SourceRevision revision = revisions
                .findByIdAndOrganizationId(revisionId, tenantId)
                .filter(value -> value.getStatus() == SourceRevisionStatus.READY)
                .filter(value -> assetId.equals(value.getKnowledgeAssetId()))
                .orElse(null);
        if (revision == null) {
            return new SourceCitationEvidenceResult.Unavailable(
                    SourceCitationEvidenceResult.Reason.REVISION_NOT_CURRENT);
        }

        EvidenceBlob blob = blobs
                .findByIdAndOrganizationId(
                        revision.getEvidenceBlobId(), tenantId)
                .filter(value -> value.getScanStatus()
                        == EvidenceScanStatus.BASIC_VALIDATED)
                .orElse(null);
        if (blob == null) {
            return new SourceCitationEvidenceResult.Unavailable(
                    SourceCitationEvidenceResult.Reason.BLOB_NOT_AVAILABLE);
        }

        return new SourceCitationEvidenceResult.Available(
                new SourceCitationEvidence(
                        revision.getFileName(),
                        revision.getMediaType(),
                        revision.getContentLength(),
                        revision.getContentSha256(),
                        new ObjectKey(blob.getObjectKey()),
                        blob.getContentLength(),
                        blob.getContentSha256()));
    }
}
