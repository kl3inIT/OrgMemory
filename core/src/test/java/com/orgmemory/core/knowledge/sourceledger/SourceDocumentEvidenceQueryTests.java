package com.orgmemory.core.knowledge.sourceledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceDocumentEvidenceQueryTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID BLOB_ID = UUID.randomUUID();

    private final SourceRevisionRepository revisions = mock(SourceRevisionRepository.class);
    private final EvidenceBlobRepository blobs = mock(EvidenceBlobRepository.class);
    private final SourceDocumentEvidenceQuery query =
            new SourceDocumentEvidenceQuery(revisions, blobs);

    @Test
    void mapsTheCurrentReadyRevisionAndValidatedBlob() {
        SourceRevision revision = mock(SourceRevision.class);
        when(revision.getId()).thenReturn(REVISION_ID);
        when(revision.getKnowledgeAssetId()).thenReturn(ASSET_ID);
        when(revision.getEvidenceBlobId()).thenReturn(BLOB_ID);
        when(revision.getFileName()).thenReturn("policy.txt");
        when(revision.getMediaType()).thenReturn("text/plain");
        when(revision.getContentLength()).thenReturn(17L);
        when(revision.getContentSha256()).thenReturn("revision-sha");
        when(revisions.findCurrentReadyBySourceObjectIdAndOrganizationId(
                        SOURCE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(revision));
        EvidenceBlob blob = mock(EvidenceBlob.class);
        when(blob.getScanStatus()).thenReturn(EvidenceScanStatus.BASIC_VALIDATED);
        when(blob.getObjectKey()).thenReturn("org/policy.txt");
        when(blob.getContentLength()).thenReturn(19L);
        when(blob.getContentSha256()).thenReturn("blob-sha");
        when(blobs.findByIdAndOrganizationId(BLOB_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(blob));

        SourceDocumentEvidence document = query
                .findAvailable(ORGANIZATION_ID, SOURCE_ID)
                .orElseThrow();

        assertEquals(REVISION_ID, document.sourceRevisionId());
        assertEquals(ASSET_ID, document.knowledgeAssetId());
        assertEquals("org/policy.txt", document.evidence().objectKey().value());
    }

    @Test
    void missingCurrentRevisionFailsClosedBeforeBlobLookup() {
        assertTrue(query.findAvailable(ORGANIZATION_ID, SOURCE_ID).isEmpty());
        verifyNoInteractions(blobs);
    }
}
