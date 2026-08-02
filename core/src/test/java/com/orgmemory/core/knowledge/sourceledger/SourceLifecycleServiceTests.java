package com.orgmemory.core.knowledge.sourceledger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceLifecycleServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final String SOURCE_SYSTEM = "slack";
    private static final String CONNECTION_KEY = "workspace-1";
    private static final String EXTERNAL_OBJECT_ID = "channel-1";

    private final SourceObjectRepository sources = mock(SourceObjectRepository.class);
    private final SourceRevisionRepository revisions = mock(SourceRevisionRepository.class);
    private final SourceLifecycleService lifecycle = new SourceLifecycleService(sources, revisions);

    @Test
    void retiresAnActiveSource() {
        SourceObject source = mock(SourceObject.class);
        when(sources.findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        ORGANIZATION_ID,
                        SOURCE_SYSTEM,
                        CONNECTION_KEY,
                        EXTERNAL_OBJECT_ID))
                .thenReturn(Optional.of(source));
        when(source.getStatus()).thenReturn(SourceObjectStatus.ACTIVE);

        assertTrue(lifecycle.retire(
                ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY, EXTERNAL_OBJECT_ID));

        verify(source).archive();
        verify(sources).saveAndFlush(source);
    }

    @Test
    void missingOrAlreadyArchivedSourcesAreUnchanged() {
        SourceObject archived = mock(SourceObject.class);
        when(archived.getStatus()).thenReturn(SourceObjectStatus.ARCHIVED);
        when(sources.findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        ORGANIZATION_ID,
                        SOURCE_SYSTEM,
                        CONNECTION_KEY,
                        "archived"))
                .thenReturn(Optional.of(archived));

        assertFalse(lifecycle.retire(
                ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY, "missing"));
        assertFalse(lifecycle.retire(
                ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY, "archived"));

        verify(archived, never()).archive();
        verify(sources, never()).saveAndFlush(archived);
    }

    @Test
    void resolvesAndArchivesOnlyAReadyNativeUpload() {
        UUID sourceId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        SourceObject source = mock(SourceObject.class);
        SourceRevision revision = mock(SourceRevision.class);
        when(sources.findByIdAndOrganizationId(sourceId, ORGANIZATION_ID))
                .thenReturn(Optional.of(source));
        when(source.getId()).thenReturn(sourceId);
        when(source.getStatus()).thenReturn(SourceObjectStatus.ACTIVE);
        when(source.getSourceSystem()).thenReturn(SourceObject.NATIVE_UPLOAD_SYSTEM);
        when(source.getCurrentRevisionId()).thenReturn(revisionId);
        when(revisions.findByIdAndOrganizationId(revisionId, ORGANIZATION_ID))
                .thenReturn(Optional.of(revision));
        when(revision.getStatus()).thenReturn(SourceRevisionStatus.READY);
        when(revision.getKnowledgeAssetId()).thenReturn(assetId);
        when(revision.getKnowledgeAssetVersionId()).thenReturn(versionId);

        ReadyManualUploadRef ref = lifecycle.requireReadyManualUpload(
                ORGANIZATION_ID, sourceId);
        lifecycle.archiveReadyManualUpload(ORGANIZATION_ID, sourceId, assetId);

        assertEquals(assetId, ref.knowledgeAssetId());
        assertEquals(versionId, ref.knowledgeAssetVersionId());
        verify(source).archive();
        verify(sources).saveAndFlush(source);
    }

    @Test
    void rejectsConnectorAndNonReadySources() {
        UUID connectorId = UUID.randomUUID();
        UUID processingId = UUID.randomUUID();
        SourceObject connector = mock(SourceObject.class);
        SourceObject processing = mock(SourceObject.class);
        when(sources.findByIdAndOrganizationId(connectorId, ORGANIZATION_ID))
                .thenReturn(Optional.of(connector));
        when(connector.getStatus()).thenReturn(SourceObjectStatus.ACTIVE);
        when(connector.getSourceSystem()).thenReturn("google-drive");
        when(sources.findByIdAndOrganizationId(processingId, ORGANIZATION_ID))
                .thenReturn(Optional.of(processing));
        when(processing.getStatus()).thenReturn(SourceObjectStatus.ACTIVE);
        when(processing.getSourceSystem()).thenReturn(SourceObject.NATIVE_UPLOAD_SYSTEM);
        when(processing.getCurrentRevisionId()).thenReturn(null);

        assertThrows(
                com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException.class,
                () -> lifecycle.requireReadyManualUpload(ORGANIZATION_ID, connectorId));
        assertThrows(
                com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException.class,
                () -> lifecycle.requireReadyManualUpload(ORGANIZATION_ID, processingId));
    }
}
