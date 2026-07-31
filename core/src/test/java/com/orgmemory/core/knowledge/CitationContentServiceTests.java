package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.sourceledger.EvidenceBlob;
import com.orgmemory.core.knowledge.sourceledger.EvidenceBlobRepository;
import com.orgmemory.core.knowledge.sourceledger.EvidenceScanStatus;
import com.orgmemory.core.knowledge.sourceledger.SourceRevision;
import com.orgmemory.core.knowledge.sourceledger.SourceRevisionRepository;
import com.orgmemory.core.knowledge.sourceledger.SourceRevisionStatus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.storage.ObjectContent;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitationContentServiceTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000002");
    private static final UUID ASSET_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000004");
    private static final UUID REVISION_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID CHUNK_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000006");
    private static final UUID BLOB_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000007");
    private static final UUID ACL_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000008");
    private static final UUID PROFILE_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000009");
    private static final String MODEL_ID = "model-v1";
    private static final byte[] CONTENT = "approved evidence".getBytes(
            java.nio.charset.StandardCharsets.UTF_8);
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID,
            ORGANIZATION_ID,
            null,
            "User",
            "user@example.test");

    @Test
    void streamsOnlyCurrentPermissionVerifiedEvidence() throws Exception {
        Fixture fixture = new Fixture();
        fixture.authorizeCitation();
        fixture.revisionAndBlob();
        fixture.objectContent();

        try (CitationContent citation =
                fixture.service.open(ACTOR, CHUNK_ID, "request-1")) {
            assertArrayEquals(CONTENT, citation.stream().readAllBytes());
        }

        verify(fixture.objects).open(any());
        verify(fixture.authorization)
                .verify(any(), any(), any(), any());
        verify(fixture.audit).record(any());
    }

    @Test
    void revocationBeforeOpeningTheBlobReturnsTheSameOpaqueNotFound() {
        Fixture fixture = new Fixture();
        when(fixture.authorization.verify(any(), any(), any(), any()))
                .thenThrow(new CanonicalEvidenceAuthorizationException(
                        "CITATION_AUTHORIZATION_CHANGED",
                        MODEL_ID));

        assertThrows(
                CitationNotFoundException.class,
                () -> fixture.service.open(
                        ACTOR, CHUNK_ID, "request-1"));

        verify(fixture.objects, never()).open(any());
    }

    private static SecureRetrievalCandidate candidate() {
        return new SecureRetrievalCandidate(
                ORGANIZATION_ID,
                CHUNK_ID,
                ASSET_ID,
                UUID.randomUUID(),
                REVISION_ID,
                "Policy",
                "approved evidence",
                null,
                null,
                null,
                null,
                0.0,
                ACL_ID,
                ACL_ID,
                MODEL_ID,
                PROFILE_ID,
                1L);
    }

    private static final class Fixture {

        private final CanonicalEvidenceAuthorizationService authorization =
                mock(CanonicalEvidenceAuthorizationService.class);
        private final SourceRevisionRepository revisions =
                mock(SourceRevisionRepository.class);
        private final EvidenceBlobRepository blobs =
                mock(EvidenceBlobRepository.class);
        private final ObjectStoragePort objects =
                mock(ObjectStoragePort.class);
        private final PermissionAuditService audit =
                mock(PermissionAuditService.class);
        private final CitationContentService service =
                new CitationContentService(
                        authorization,
                        revisions,
                        blobs,
                        objects,
                        audit);

        private void authorizeCitation() {
            when(authorization.verify(any(), any(), any(), any()))
                    .thenReturn(new CanonicalEvidenceAuthorizationService
                            .Verification(
                            MODEL_ID,
                            List.of(candidate())));
        }

        private void revisionAndBlob() {
            SourceRevision revision = mock(SourceRevision.class);
            when(revision.getStatus())
                    .thenReturn(SourceRevisionStatus.READY);
            when(revision.getKnowledgeAssetId())
                    .thenReturn(ASSET_ID);
            when(revision.getEvidenceBlobId())
                    .thenReturn(BLOB_ID);
            when(revision.getFileName())
                    .thenReturn("policy.txt");
            when(revision.getMediaType()).thenReturn("text/plain");
            when(revision.getContentLength())
                    .thenReturn((long) CONTENT.length);
            when(revision.getContentSha256()).thenReturn("sha256");
            when(revisions.findByIdAndOrganizationId(
                            REVISION_ID, ORGANIZATION_ID))
                    .thenReturn(java.util.Optional.of(revision));

            EvidenceBlob blob = mock(EvidenceBlob.class);
            when(blob.getScanStatus())
                    .thenReturn(EvidenceScanStatus.BASIC_VALIDATED);
            when(blob.getObjectKey()).thenReturn("org/policy.txt");
            when(blob.getContentLength())
                    .thenReturn((long) CONTENT.length);
            when(blob.getContentSha256()).thenReturn("sha256");
            when(blobs.findByIdAndOrganizationId(
                            BLOB_ID, ORGANIZATION_ID))
                    .thenReturn(java.util.Optional.of(blob));
        }

        private void objectContent() {
            objectContent(new ByteArrayInputStream(CONTENT));
        }

        private void objectContent(InputStream stream) {
            StoredObject metadata = new StoredObject(
                    new com.orgmemory.core.knowledge.storage.ObjectKey(
                            "org/policy.txt"),
                    CONTENT.length,
                    "text/plain",
                    "sha256",
                    null,
                    null);
            when(objects.open(any()))
                    .thenReturn(new ObjectContent(
                            stream,
                            metadata));
        }
    }
}
