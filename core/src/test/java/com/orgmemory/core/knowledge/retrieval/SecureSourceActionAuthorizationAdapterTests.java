package com.orgmemory.core.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecureSourceActionAuthorizationAdapterTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID, ORGANIZATION_ID, null, "User", "user@example.test");

    private final RelationshipAuthorizationSetPort authorization =
            mock(RelationshipAuthorizationSetPort.class);
    private final SecureSourceActionAuthorizationAdapter adapter =
            new SecureSourceActionAuthorizationAdapter(
                    authorization,
                    new KnowledgeRetrievalProperties(null, null, null, null));

    @Test
    void returnsOnlyTenantScopedKnowledgeAssetIds() {
        UUID assetId = UUID.randomUUID();
        when(authorization.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.resolved(
                        List.of(ResourceRef.of(
                                ORGANIZATION_ID, "knowledge_asset", assetId)),
                        "model-v1"));

        assertEquals(Set.of(assetId), adapter.deletableKnowledgeAssetIds(ACTOR));
    }

    @Test
    void outageDisablesTheHintAndMalformedResourcesFailClosed() {
        when(authorization.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.indeterminate(
                        "OPENFGA_UNAVAILABLE", "model-v1"));
        assertEquals(Set.of(), adapter.deletableKnowledgeAssetIds(ACTOR));

        when(authorization.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.resolved(
                        List.of(ResourceRef.of(
                                UUID.randomUUID(), "knowledge_asset", UUID.randomUUID())),
                        "model-v1"));
        assertThrows(
                KnowledgeRetrievalUnavailableException.class,
                () -> adapter.deletableKnowledgeAssetIds(ACTOR));
    }
}
