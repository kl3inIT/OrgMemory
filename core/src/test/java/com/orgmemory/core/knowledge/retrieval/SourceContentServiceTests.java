package com.orgmemory.core.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.sourceledger.EvidenceBlob;
import com.orgmemory.core.knowledge.sourceledger.EvidenceBlobRepository;
import com.orgmemory.core.knowledge.sourceledger.EvidenceScanStatus;
import com.orgmemory.core.knowledge.sourceledger.SourceRevision;
import com.orgmemory.core.knowledge.sourceledger.SourceRevisionRepository;
import com.orgmemory.core.knowledge.storage.ObjectContent;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SourceContentServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID BLOB_ID = UUID.randomUUID();
    private static final byte[] CONTENT = "approved handbook".getBytes(StandardCharsets.UTF_8);
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID, ORGANIZATION_ID, null, "User", "user@example.test");

    private final KnowledgeEvidenceScopeResolver authorization = mock(KnowledgeEvidenceScopeResolver.class);
    private final SourceRevisionRepository revisions = mock(SourceRevisionRepository.class);
    private final EvidenceBlobRepository blobs = mock(EvidenceBlobRepository.class);
    private final ObjectStoragePort objects = mock(ObjectStoragePort.class);
    private final PermissionAuditService audit = mock(PermissionAuditService.class);
    private final SourceContentService service = new SourceContentService(
            authorization, revisions, blobs, objects, audit);

    @Test
    void streamsOnlyTheCurrentPermissionVisibleReadyRevision() throws Exception {
        sourceRevisionAndBlob();
        authorizeAsset();
        when(objects.open(any())).thenReturn(new ObjectContent(
                new ByteArrayInputStream(CONTENT),
                new StoredObject(
                        new ObjectKey("org/handbook.txt"),
                        CONTENT.length,
                        "text/plain",
                        "sha256",
                        null,
                        null)));

        try (SourceContent content = service.open(ACTOR, SOURCE_ID, "request-1")) {
            assertArrayEquals(CONTENT, content.stream().readAllBytes());
        }

        verify(objects).open(any());
        verify(audit).record(any());
    }

    @Test
    void deniedAndMissingSourcesShareTheOpaqueNotFoundContract() {
        sourceRevisionAndBlob();
        when(authorization.resolve(ACTOR, null)).thenReturn(new ResolvedKnowledgeEvidenceScope(
                ORGANIZATION_ID,
                USER_ID,
                null,
                false,
                "model-v1",
                Instant.now(),
                Map.of(),
                Map.of()));

        assertThrows(
                KnowledgeResourceNotFoundException.class,
                () -> service.open(ACTOR, SOURCE_ID, "request-1"));

        verify(objects, never()).open(any());
        verify(audit).record(any());
    }

    @Test
    void integrityFailureClosesTheObjectAndIsAudited() throws Exception {
        sourceRevisionAndBlob();
        authorizeAsset();
        var stream = mock(java.io.InputStream.class);
        when(objects.open(any())).thenReturn(new ObjectContent(
                stream,
                new StoredObject(
                        new ObjectKey("org/handbook.txt"),
                        CONTENT.length,
                        "text/plain",
                        "different-sha256",
                        null,
                        null)));

        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> service.open(ACTOR, SOURCE_ID, "request-integrity"));

        verify(stream).close();
        verify(audit).record(any());
    }

    private void sourceRevisionAndBlob() {
        SourceRevision revision = mock(SourceRevision.class);
        when(revision.getId()).thenReturn(REVISION_ID);
        when(revision.getEvidenceBlobId()).thenReturn(BLOB_ID);
        when(revision.getFileName()).thenReturn("handbook.txt");
        when(revision.getMediaType()).thenReturn("text/plain");
        when(revision.getContentLength()).thenReturn((long) CONTENT.length);
        when(revision.getContentSha256()).thenReturn("sha256");
        when(revisions.findCurrentReadyBySourceObjectIdAndOrganizationId(
                        SOURCE_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(revision));

        EvidenceBlob blob = mock(EvidenceBlob.class);
        when(blob.getScanStatus()).thenReturn(EvidenceScanStatus.BASIC_VALIDATED);
        when(blob.getObjectKey()).thenReturn("org/handbook.txt");
        when(blob.getContentLength()).thenReturn((long) CONTENT.length);
        when(blob.getContentSha256()).thenReturn("sha256");
        when(blobs.findByIdAndOrganizationId(BLOB_ID, ORGANIZATION_ID))
                .thenReturn(Optional.of(blob));
    }

    private void authorizeAsset() {
        UUID assetId = UUID.randomUUID();
        SourceRevision revision = revisions
                .findCurrentReadyBySourceObjectIdAndOrganizationId(SOURCE_ID, ORGANIZATION_ID)
                .orElseThrow();
        when(revision.getKnowledgeAssetId()).thenReturn(assetId);
        UUID spaceId = UUID.randomUUID();
        when(authorization.resolve(ACTOR, null)).thenReturn(new ResolvedKnowledgeEvidenceScope(
                ORGANIZATION_ID,
                USER_ID,
                null,
                false,
                "model-v1",
                Instant.now(),
                Map.of(spaceId, Set.of(assetId)),
                Map.of(spaceId, 1L)));
    }
}
