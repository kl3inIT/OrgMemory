package com.orgmemory.core.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.sourceledger.SourceCitationEvidence;
import com.orgmemory.core.knowledge.sourceledger.SourceCitationEvidenceQuery;
import com.orgmemory.core.knowledge.sourceledger.SourceCitationEvidenceResult;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CitationEvidenceServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID CHUNK_ID = UUID.randomUUID();
    private static final String MODEL_ID = "model-v1";
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID, ORGANIZATION_ID, null, "User", "user@example.test");

    @Test
    void hydratesOnlyCurrentlyVisibleMetadataAndDeduplicatesInput() {
        Fixture fixture = new Fixture();
        when(fixture.authorization.filterVisible(any(), any(), any(), any()))
                .thenReturn(new CanonicalEvidenceAuthorizationService.Verification(
                        MODEL_ID, List.of(candidate("Short evidence"))));

        var hydrated = fixture.service.hydrate(
                ACTOR, List.of(CHUNK_ID, CHUNK_ID), "request-1");

        assertEquals(1, hydrated.size());
        assertEquals(CHUNK_ID, hydrated.getFirst().chunkId());
        assertEquals("Policy", hydrated.getFirst().title());
    }

    @Test
    void excerptIsBoundedByUnicodeCodePointsAndUsesClosedFilenamePresentation() {
        Fixture fixture = new Fixture();
        String content = "😀".repeat(4_001);
        fixture.authorize(content);
        fixture.available("policy.md");

        CitationEvidenceExcerpt excerpt = fixture.service.excerpt(
                ACTOR, CHUNK_ID, "request-2");

        assertEquals(4_000, excerpt.excerpt().codePointCount(0, excerpt.excerpt().length()));
        assertEquals(true, excerpt.truncated());
        assertEquals(CitationPresentationKind.MARKDOWN, excerpt.presentationKind());
        ArgumentCaptor<PermissionAuditCommand> audit =
                ArgumentCaptor.forClass(PermissionAuditCommand.class);
        verify(fixture.audit).record(audit.capture());
        assertEquals(PermissionAuditDecision.ALLOW, audit.getValue().decision());
        assertEquals("AUTHORIZED_CITATION_EXCERPT", audit.getValue().reasonCode());
    }

    @Test
    void revokedExcerptUsesTheOpaqueCitationNotFoundSurface() {
        Fixture fixture = new Fixture();
        when(fixture.authorization.verify(any(), any(), any(), any()))
                .thenThrow(new CanonicalEvidenceAuthorizationException(
                        "CITATION_NOT_VISIBLE", MODEL_ID));

        assertThrows(
                CitationNotFoundException.class,
                () -> fixture.service.excerpt(ACTOR, CHUNK_ID, "request-3"));
    }

    @Test
    void selectsOnlyClosedFilenameBasedPresentationKinds() {
        Map<String, CitationPresentationKind> cases = Map.of(
                "policy.pdf", CitationPresentationKind.PDF,
                "policy.md", CitationPresentationKind.MARKDOWN,
                "policy.txt", CitationPresentationKind.PLAIN_TEXT,
                "chart.webp", CitationPresentationKind.IMAGE,
                "policy.docx", CitationPresentationKind.DOWNLOAD,
                "archive.bin", CitationPresentationKind.DOWNLOAD);

        cases.forEach((fileName, expected) -> {
            Fixture fixture = new Fixture();
            fixture.authorize("governed evidence");
            fixture.available(fileName);

            assertEquals(
                    expected,
                    fixture.service.excerpt(ACTOR, CHUNK_ID, "request-kind")
                            .presentationKind(),
                    fileName);
        });
    }

    private static SecureRetrievalCandidate candidate(String content) {
        UUID acl = UUID.randomUUID();
        return new SecureRetrievalCandidate(
                ORGANIZATION_ID,
                CHUNK_ID,
                ASSET_ID,
                UUID.randomUUID(),
                REVISION_ID,
                "Policy",
                content,
                null,
                2,
                3,
                "Probation",
                1.0,
                acl,
                acl,
                MODEL_ID,
                UUID.randomUUID(),
                1L);
    }

    private static final class Fixture {
        private final CanonicalEvidenceAuthorizationService authorization =
                mock(CanonicalEvidenceAuthorizationService.class);
        private final SourceCitationEvidenceQuery evidence =
                mock(SourceCitationEvidenceQuery.class);
        private final PermissionAuditService audit = mock(PermissionAuditService.class);
        private final CitationEvidenceService service =
                new DefaultCitationEvidenceService(authorization, evidence, audit);

        private void authorize(String content) {
            when(authorization.verify(any(), any(), any(), any()))
                    .thenReturn(new CanonicalEvidenceAuthorizationService.Verification(
                            MODEL_ID, List.of(candidate(content))));
        }

        private void available(String fileName) {
            when(evidence.findAvailable(ORGANIZATION_ID, REVISION_ID, ASSET_ID))
                    .thenReturn(new SourceCitationEvidenceResult.Available(
                            new SourceCitationEvidence(
                                    fileName,
                                    "text/plain",
                                    10,
                                    "sha256",
                                    new com.orgmemory.core.knowledge.storage.ObjectKey("evidence/file"),
                                    10,
                                    "sha256")));
        }
    }
}
