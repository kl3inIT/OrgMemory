package com.orgmemory.core.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.sourceledger.SourceCitationEvidence;
import com.orgmemory.core.knowledge.sourceledger.SourceDocumentEvidence;
import com.orgmemory.core.knowledge.sourceledger.SourceDocumentEvidenceQuery;
import com.orgmemory.core.knowledge.storage.ObjectContent;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final byte[] CONTENT = "approved handbook".getBytes(StandardCharsets.UTF_8);
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID, ORGANIZATION_ID, null, "User", "user@example.test");

    private final KnowledgeEvidenceScopeResolver authorization = mock(KnowledgeEvidenceScopeResolver.class);
    private final SourceDocumentEvidenceQuery evidenceQuery = mock(SourceDocumentEvidenceQuery.class);
    private final ObjectStoragePort objects = mock(ObjectStoragePort.class);
    private final PermissionAuditService audit = mock(PermissionAuditService.class);
    private final SourceContentService service = new DefaultSourceContentService(
            authorization, evidenceQuery, objects, audit);

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
        when(evidenceQuery.findAvailable(ORGANIZATION_ID, SOURCE_ID)).thenReturn(
                Optional.of(new SourceDocumentEvidence(
                        REVISION_ID,
                        ASSET_ID,
                        null,
                        new SourceCitationEvidence(
                                "handbook.txt",
                                "text/plain",
                                CONTENT.length,
                                "sha256",
                                new ObjectKey("org/handbook.txt"),
                                CONTENT.length,
                                "sha256"))));
    }

    private void authorizeAsset() {
        UUID spaceId = UUID.randomUUID();
        when(authorization.resolve(ACTOR, null)).thenReturn(new ResolvedKnowledgeEvidenceScope(
                ORGANIZATION_ID,
                USER_ID,
                null,
                false,
                "model-v1",
                Instant.now(),
                Map.of(spaceId, Set.of(ASSET_ID)),
                Map.of(spaceId, 1L)));
    }
}
