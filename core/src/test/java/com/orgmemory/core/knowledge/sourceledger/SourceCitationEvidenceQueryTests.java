package com.orgmemory.core.knowledge.sourceledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceCitationEvidenceQueryTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID BLOB_ID = UUID.randomUUID();

    private final SourceRevisionRepository revisions = mock(SourceRevisionRepository.class);
    private final EvidenceBlobRepository blobs = mock(EvidenceBlobRepository.class);
    private final SourceCitationEvidenceQuery query =
            new SourceCitationEvidenceQuery(revisions, blobs);

    @Test
    void mapsTenantScopedReadyRevisionAndValidatedBlob() {
        SourceRevision revision = readyRevision();
        EvidenceBlob blob = validatedBlob();
        when(revisions.findByIdAndOrganizationId(REVISION_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(revision));
        when(blobs.findByIdAndOrganizationId(BLOB_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(blob));

        var available = assertInstanceOf(
                SourceCitationEvidenceResult.Available.class,
                query.findAvailable(ORGANIZATION_ID, REVISION_ID, ASSET_ID));

        assertEquals(
                new SourceCitationEvidence(
                        "policy.txt",
                        "text/plain",
                        17L,
                        "revision-sha",
                        new com.orgmemory.core.knowledge.storage.ObjectKey(
                                "org/policy.txt"),
                        19L,
                        "blob-sha"),
                available.evidence());
        verify(revisions).findByIdAndOrganizationId(
                REVISION_ID, ORGANIZATION_ID);
        verify(blobs).findByIdAndOrganizationId(BLOB_ID, ORGANIZATION_ID);
    }

    @Test
    void missingOrNonReadyRevisionNeverLooksUpABlob() {
        assertUnavailable(
                SourceCitationEvidenceResult.Reason.REVISION_NOT_CURRENT,
                query.findAvailable(ORGANIZATION_ID, REVISION_ID, ASSET_ID));

        SourceRevision revision = mock(SourceRevision.class);
        when(revision.getStatus()).thenReturn(SourceRevisionStatus.PARSING);
        when(revisions.findByIdAndOrganizationId(REVISION_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(revision));

        assertUnavailable(
                SourceCitationEvidenceResult.Reason.REVISION_NOT_CURRENT,
                query.findAvailable(ORGANIZATION_ID, REVISION_ID, ASSET_ID));
        verifyNoInteractions(blobs);
    }

    @Test
    void revisionForAnotherAssetFailsBeforeBlobLookup() {
        SourceRevision revision = readyRevision();
        when(revision.getKnowledgeAssetId()).thenReturn(UUID.randomUUID());
        when(revisions.findByIdAndOrganizationId(REVISION_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(revision));

        assertUnavailable(
                SourceCitationEvidenceResult.Reason.REVISION_NOT_CURRENT,
                query.findAvailable(ORGANIZATION_ID, REVISION_ID, ASSET_ID));
        verify(blobs, never()).findByIdAndOrganizationId(BLOB_ID, ORGANIZATION_ID);
    }

    @Test
    void missingOrUnvalidatedBlobKeepsItsDistinctReason() {
        SourceRevision revision = readyRevision();
        when(revisions.findByIdAndOrganizationId(REVISION_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(revision));

        assertUnavailable(
                SourceCitationEvidenceResult.Reason.BLOB_NOT_AVAILABLE,
                query.findAvailable(ORGANIZATION_ID, REVISION_ID, ASSET_ID));

        EvidenceBlob blob = mock(EvidenceBlob.class);
        when(blob.getScanStatus()).thenReturn(EvidenceScanStatus.REJECTED);
        when(blobs.findByIdAndOrganizationId(BLOB_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(blob));

        assertUnavailable(
                SourceCitationEvidenceResult.Reason.BLOB_NOT_AVAILABLE,
                query.findAvailable(ORGANIZATION_ID, REVISION_ID, ASSET_ID));
    }

    private static void assertUnavailable(
            SourceCitationEvidenceResult.Reason expected,
            SourceCitationEvidenceResult result) {
        var unavailable = assertInstanceOf(
                SourceCitationEvidenceResult.Unavailable.class, result);
        assertEquals(expected, unavailable.reason());
    }

    private static SourceRevision readyRevision() {
        SourceRevision revision = mock(SourceRevision.class);
        when(revision.getStatus()).thenReturn(SourceRevisionStatus.READY);
        when(revision.getKnowledgeAssetId()).thenReturn(ASSET_ID);
        when(revision.getEvidenceBlobId()).thenReturn(BLOB_ID);
        when(revision.getFileName()).thenReturn("policy.txt");
        when(revision.getMediaType()).thenReturn("text/plain");
        when(revision.getContentLength()).thenReturn(17L);
        when(revision.getContentSha256()).thenReturn("revision-sha");
        return revision;
    }

    private static EvidenceBlob validatedBlob() {
        EvidenceBlob blob = mock(EvidenceBlob.class);
        when(blob.getScanStatus()).thenReturn(EvidenceScanStatus.BASIC_VALIDATED);
        when(blob.getObjectKey()).thenReturn("org/policy.txt");
        when(blob.getContentLength()).thenReturn(19L);
        when(blob.getContentSha256()).thenReturn("blob-sha");
        return blob;
    }
}
