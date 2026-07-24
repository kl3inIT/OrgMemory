package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.BatchAuthorizationResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.storage.ObjectContent;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditService;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitationContentServiceTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000002");
    private static final UUID SPACE_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000003");
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
        fixture.authorize();
        fixture.currentScopes();
        fixture.canonicalEvidence();
        fixture.revisionAndBlob();
        fixture.objectContent();

        try (CitationContent citation =
                fixture.service.open(ACTOR, CHUNK_ID, "request-1")) {
            assertArrayEquals(CONTENT, citation.stream().readAllBytes());
        }

        verify(fixture.objects).open(any());
        verify(fixture.audit).record(any());
    }

    @Test
    void revocationBeforeOpeningTheBlobReturnsTheSameOpaqueNotFound() {
        Fixture fixture = new Fixture();
        fixture.authorize();
        when(fixture.scopes.resolve(ACTOR, MODEL_ID))
                .thenReturn(scope(Set.of(ASSET_ID)), scope(Set.of()));
        fixture.canonicalEvidence();

        assertThrows(
                CitationNotFoundException.class,
                () -> fixture.service.open(
                        ACTOR, CHUNK_ID, "request-1"));

        verify(fixture.objects, never()).open(any());
    }

    private static ResolvedKnowledgeEvidenceScope scope(Set<UUID> assets) {
        return new ResolvedKnowledgeEvidenceScope(
                ORGANIZATION_ID,
                USER_ID,
                null,
                false,
                MODEL_ID,
                Instant.parse("2026-07-24T00:00:00Z"),
                assets.isEmpty()
                        ? Map.of()
                        : Map.of(SPACE_ID, assets),
                assets.isEmpty()
                        ? Map.of()
                        : Map.of(SPACE_ID, 1L));
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

        private final KnowledgeSearchAuthorizationService search =
                mock(KnowledgeSearchAuthorizationService.class);
        private final KnowledgeEvidenceScopeResolver scopes =
                mock(KnowledgeEvidenceScopeResolver.class);
        private final RelationshipAuthorizationSetPort authorization =
                mock(RelationshipAuthorizationSetPort.class);
        private final SecureKnowledgeRetrievalStore canonical =
                mock(SecureKnowledgeRetrievalStore.class);
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
                        search,
                        scopes,
                        authorization,
                        canonical,
                        revisions,
                        blobs,
                        objects,
                        audit);

        private void authorize() {
            when(search.require(ACTOR, "request-1", "citation:" + CHUNK_ID))
                    .thenReturn(MODEL_ID);
            ResourceRef resource = ResourceRef.of(
                    ORGANIZATION_ID,
                    "knowledge_asset",
                    ASSET_ID);
            when(authorization.batchCheck(any()))
                    .thenReturn(BatchAuthorizationResult.resolved(
                            Map.of(
                                    resource,
                                    AuthorizationDecision.allow(
                                            MODEL_ID)),
                            MODEL_ID));
        }

        private void currentScopes() {
            when(scopes.resolve(ACTOR, MODEL_ID))
                    .thenReturn(scope(Set.of(ASSET_ID)));
        }

        private void canonicalEvidence() {
            when(canonical.recheck(any(), any()))
                    .thenReturn(List.of(candidate()));
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
                            new ByteArrayInputStream(CONTENT),
                            metadata));
        }
    }
}
