package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.BatchAuthorizationResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CanonicalEvidenceAuthorizationServiceTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000002");
    private static final UUID SPACE_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000003");
    private static final UUID ASSET_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000004");
    private static final UUID OBJECT_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000005");
    private static final UUID REVISION_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000006");
    private static final UUID CHUNK_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000007");
    private static final UUID ACL_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000008");
    private static final UUID PROFILE_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000010");
    private static final String MODEL_ID = "model-v1";
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID,
            ORGANIZATION_ID,
            null,
            "User",
            "user@example.test");

    @Test
    void verifiesCurrentCanonicalEvidenceOncePerCitationRequest() {
        Fixture fixture = new Fixture();
        fixture.allow();
        SecureRetrievalCandidate candidate = candidate(ACL_ID);
        when(fixture.canonical.recheck(any(), any()))
                .thenReturn(List.of(candidate));

        var verified = fixture.service.verify(
                ACTOR,
                "request-1",
                "citation:request-1",
                List.of(CHUNK_ID));

        assertEquals(List.of(candidate), verified.candidates());
        assertEquals(MODEL_ID, verified.authorizationModelId());
    }

    @Test
    void missingCanonicalEvidenceFailsClosed() {
        Fixture fixture = new Fixture();
        fixture.allow();
        when(fixture.canonical.recheck(any(), any()))
                .thenReturn(List.of());

        CanonicalEvidenceAuthorizationException denied = assertThrows(
                CanonicalEvidenceAuthorizationException.class,
                () -> fixture.service.verify(
                        ACTOR,
                        "request-1",
                        "citation:request-1",
                        List.of(CHUNK_ID)));

        assertEquals(
                "CITATION_NOT_VISIBLE",
                denied.reasonCode());
    }

    @Test
    void finalOpenFgaDenialFailsClosed() {
        Fixture fixture = new Fixture();
        when(fixture.search.require(any(), any(), any()))
                .thenReturn(MODEL_ID);
        when(fixture.scopes.resolve(ACTOR, MODEL_ID))
                .thenReturn(scope());
        when(fixture.canonical.recheck(any(), any()))
                .thenReturn(List.of(candidate(ACL_ID)));
        ResourceRef resource = ResourceRef.of(
                ORGANIZATION_ID,
                "knowledge_asset",
                ASSET_ID);
        when(fixture.authorization.batchCheck(any()))
                .thenReturn(BatchAuthorizationResult.resolved(
                        Map.of(
                                resource,
                                AuthorizationDecision.deny(
                                        "RELATIONSHIP_DENIED",
                                        MODEL_ID)),
                        MODEL_ID));

        CanonicalEvidenceAuthorizationException denied = assertThrows(
                CanonicalEvidenceAuthorizationException.class,
                () -> fixture.service.verify(
                        ACTOR,
                        "request-1",
                        "citation:request-1",
                        List.of(CHUNK_ID)));

        assertEquals(
                "CITATION_OPENFGA_RECHECK_DENIED",
                denied.reasonCode());
    }

    private static ResolvedKnowledgeEvidenceScope scope() {
        return new ResolvedKnowledgeEvidenceScope(
                ORGANIZATION_ID,
                USER_ID,
                null,
                false,
                MODEL_ID,
                Instant.parse("2026-07-24T00:00:00Z"),
                Map.of(SPACE_ID, Set.of(ASSET_ID)),
                Map.of(SPACE_ID, 1L));
    }

    private static SecureRetrievalCandidate candidate(UUID aclId) {
        return new SecureRetrievalCandidate(
                ORGANIZATION_ID,
                CHUNK_ID,
                ASSET_ID,
                OBJECT_ID,
                REVISION_ID,
                "Policy",
                "Approved evidence",
                null,
                null,
                null,
                null,
                0.0,
                ACL_ID,
                aclId,
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
        private final CanonicalEvidenceAuthorizationService service =
                new CanonicalEvidenceAuthorizationService(
                        search,
                        scopes,
                        authorization,
                        canonical);

        private void allow() {
            when(search.require(any(), any(), any()))
                    .thenReturn(MODEL_ID);
            when(scopes.resolve(ACTOR, MODEL_ID))
                    .thenReturn(scope());
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
    }
}
