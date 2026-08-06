package com.orgmemory.core.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.KnowledgeAccessSubject;
import com.orgmemory.core.organization.KnowledgeAccessSubjectQuery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecureSourceVisibilityAdapterTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID,
            ORGANIZATION_ID,
            DEPARTMENT_ID,
            "Nguyen Van An",
            "an@example.com");

    private final KnowledgeAccessSubjectQuery subjects =
            mock(KnowledgeAccessSubjectQuery.class);
    private final RelationshipAuthorizationSetPort authorization =
            mock(RelationshipAuthorizationSetPort.class);
    private final SecureKnowledgeRetrievalStore visibility =
            mock(SecureKnowledgeRetrievalStore.class);
    private final KnowledgeRetrievalProperties properties =
            new KnowledgeRetrievalProperties(null, null, null, null);
    private final SecureSourceVisibilityAdapter adapter = new SecureSourceVisibilityAdapter(
            subjects, authorization, visibility, properties);

    @Test
    void resolvesAuthorizedAssetsThroughTheSecureRetrievalStore() {
        UUID assetId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(subjects.findActive(ORGANIZATION_ID, USER_ID))
                .thenReturn(Optional.of(subject(false)));
        when(authorization.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.resolved(
                        List.of(ResourceRef.of(
                                ORGANIZATION_ID, "knowledge_asset", assetId)),
                        "model-1"));
        when(visibility.visibleSourceObjectIds(any())).thenReturn(List.of(sourceId));

        assertEquals(List.of(sourceId), adapter.visibleSourceObjectIds(ACTOR));
        verify(visibility).visibleSourceObjectIds(any());
    }

    @Test
    void authorizationOutageFailsClosed() {
        when(subjects.findActive(ORGANIZATION_ID, USER_ID))
                .thenReturn(Optional.of(subject(false)));
        when(authorization.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.indeterminate(
                        "OPENFGA_UNAVAILABLE", "model-1"));

        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> adapter.visibleSourceObjectIds(ACTOR));
    }

    @Test
    void platformAdminDoesNotBypassDataAuthorization() {
        when(subjects.findActive(ORGANIZATION_ID, USER_ID))
                .thenReturn(Optional.of(subject(false)));
        when(authorization.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.indeterminate(
                        "OPENFGA_UNAVAILABLE", "model-1"));

        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> adapter.visibleSourceObjectIds(ACTOR));
        verify(authorization).listAuthorizedResources(any());
    }

    @Test
    void authorizedSourceUnionUsesTheRetrievalBoundAndUnavailableFailure() {
        assertEquals(5_000, adapter.maximumAuthorizedObjects());
        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> adapter.requireWithinMaximumAuthorizedObjects(5_001));
    }

    private static KnowledgeAccessSubject subject(boolean executive) {
        return new KnowledgeAccessSubject(
                USER_ID,
                ORGANIZATION_ID,
                DEPARTMENT_ID,
                executive);
    }
}
