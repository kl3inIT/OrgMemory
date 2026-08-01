package com.orgmemory.core.knowledge.sourceledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceGraphIndexQueryTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID EMBEDDING_PROFILE_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();

    private final SourceRevisionRepository revisions = mock(SourceRevisionRepository.class);
    private final SourceGraphIndexQuery query = new SourceGraphIndexQuery(revisions);

    @Test
    void exposesTenantScopedImmutableGraphRevisionState() {
        SourceRevision revision = mock(SourceRevision.class);
        when(revisions.findByIdAndOrganizationId(REVISION_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(revision));
        when(revision.getId()).thenReturn(REVISION_ID);
        when(revision.getEmbeddingProfileId()).thenReturn(EMBEDDING_PROFILE_ID);
        when(revision.getKnowledgeAssetId()).thenReturn(ASSET_ID);
        when(revision.getKnowledgeAssetVersionId()).thenReturn(VERSION_ID);
        when(revision.getStatus()).thenReturn(SourceRevisionStatus.READY);

        SourceGraphIndexRevisionRef ref =
                query.findRevision(ORGANIZATION_ID, REVISION_ID).orElseThrow();

        assertEquals(REVISION_ID, ref.id());
        assertEquals(EMBEDDING_PROFILE_ID, ref.embeddingProfileId());
        assertEquals(ASSET_ID, ref.knowledgeAssetId());
        assertEquals(VERSION_ID, ref.knowledgeAssetVersionId());
        assertTrue(ref.ready());
    }

    @Test
    void preservesMissingAndNonReadyRevisionState() {
        assertTrue(query.findRevision(ORGANIZATION_ID, REVISION_ID).isEmpty());

        SourceRevision revision = mock(SourceRevision.class);
        when(revisions.findByIdAndOrganizationId(REVISION_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(revision));
        when(revision.getId()).thenReturn(REVISION_ID);
        when(revision.getStatus()).thenReturn(SourceRevisionStatus.PARSING);

        assertFalse(query.findRevision(ORGANIZATION_ID, REVISION_ID)
                .orElseThrow()
                .ready());
    }
}
