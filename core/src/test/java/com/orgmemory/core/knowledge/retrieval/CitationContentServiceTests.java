package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.knowledge.sourceledger.SourceCitationEvidence;
import com.orgmemory.core.knowledge.sourceledger.SourceCitationEvidenceQuery;
import com.orgmemory.core.knowledge.sourceledger.SourceCitationEvidenceResult;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.mockito.ArgumentCaptor;

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
        fixture.citationEvidence();
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

    @Test
    void missingRevisionRetainsItsOpaqueAuditReason() {
        unavailableEvidenceRetainsItsOpaqueAuditReason(
                SourceCitationEvidenceResult.Reason.REVISION_NOT_CURRENT,
                "CITATION_REVISION_NOT_CURRENT");
    }

    @Test
    void unavailableBlobRetainsItsOpaqueAuditReason() {
        unavailableEvidenceRetainsItsOpaqueAuditReason(
                SourceCitationEvidenceResult.Reason.BLOB_NOT_AVAILABLE,
                "CITATION_BLOB_NOT_AVAILABLE");
    }

    @Test
    void storageIntegrityMismatchClosesContentBeforeAllowAudit() throws Exception {
        Fixture fixture = new Fixture();
        fixture.authorizeCitation();
        fixture.citationEvidence();
        InputStream stream = mock(InputStream.class);
        StoredObject metadata = new StoredObject(
                new com.orgmemory.core.knowledge.storage.ObjectKey(
                        "org/policy.txt"),
                CONTENT.length,
                "text/plain",
                "different-sha",
                null,
                null);
        when(fixture.objects.open(any()))
                .thenReturn(new ObjectContent(stream, metadata));

        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> fixture.service.open(ACTOR, CHUNK_ID, "request-1"));

        verify(stream).close();
        verify(fixture.audit, never()).record(any());
    }

    private static void unavailableEvidenceRetainsItsOpaqueAuditReason(
            SourceCitationEvidenceResult.Reason reason,
            String expectedAuditReason) {
        Fixture fixture = new Fixture();
        fixture.authorizeCitation();
        when(fixture.evidenceQuery.findAvailable(
                        ORGANIZATION_ID, REVISION_ID, ASSET_ID))
                .thenReturn(new SourceCitationEvidenceResult.Unavailable(reason));

        assertThrows(
                CitationNotFoundException.class,
                () -> fixture.service.open(ACTOR, CHUNK_ID, "request-1"));

        ArgumentCaptor<com.orgmemory.core.permission.PermissionAuditCommand> audit =
                ArgumentCaptor.forClass(
                        com.orgmemory.core.permission.PermissionAuditCommand.class);
        verify(fixture.audit).record(audit.capture());
        assertEquals(expectedAuditReason, audit.getValue().reasonCode());
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
        private final SourceCitationEvidenceQuery evidenceQuery =
                mock(SourceCitationEvidenceQuery.class);
        private final ObjectStoragePort objects =
                mock(ObjectStoragePort.class);
        private final PermissionAuditService audit =
                mock(PermissionAuditService.class);
        private final CitationContentService service =
                new DefaultCitationContentService(
                        authorization,
                        evidenceQuery,
                        objects,
                        audit);

        private void authorizeCitation() {
            when(authorization.verify(any(), any(), any(), any()))
                    .thenReturn(new CanonicalEvidenceAuthorizationService
                            .Verification(
                            MODEL_ID,
                            List.of(candidate())));
        }

        private void citationEvidence() {
            when(evidenceQuery.findAvailable(
                            ORGANIZATION_ID, REVISION_ID, ASSET_ID))
                    .thenReturn(new SourceCitationEvidenceResult.Available(
                            new SourceCitationEvidence(
                                    "policy.txt",
                                    "text/plain",
                                    CONTENT.length,
                                    "sha256",
                                    new com.orgmemory.core.knowledge.storage.ObjectKey(
                                            "org/policy.txt"),
                                    CONTENT.length,
                                    "sha256")));
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
