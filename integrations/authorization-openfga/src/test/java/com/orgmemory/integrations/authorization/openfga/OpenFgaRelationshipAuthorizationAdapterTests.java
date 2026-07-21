package com.orgmemory.integrations.authorization.openfga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.ContextualRelationship;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.PrincipalRef;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.ResourceRef;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientCheckResponse;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenFgaRelationshipAuthorizationAdapterTests {

    @Test
    void mapsTheProviderNeutralQueryToAnOpenFgaCheck() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        ClientCheckResponse response = mock(ClientCheckResponse.class);
        when(response.getAllowed()).thenReturn(true);
        when(client.check(any(ClientCheckRequest.class))).thenReturn(CompletableFuture.completedFuture(response));
        var adapter = new OpenFgaRelationshipAuthorizationAdapter(client, "model-1");
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        AuthorizationDecision decision = adapter.check(new RelationshipAuthorizationQuery(
                PrincipalRef.user(userId),
                PermissionKey.of("can_view"),
                ResourceRef.of(organizationId, "knowledge_asset", assetId),
                List.of(ContextualRelationship.of(
                        "organization:" + organizationId,
                        "organization",
                        "knowledge_asset:" + assetId))));

        assertTrue(decision.allowed());
        assertEquals("model-1", decision.policyVersion());
        var request = ArgumentCaptor.forClass(ClientCheckRequest.class);
        verify(client).check(request.capture());
        assertEquals("user:" + userId, request.getValue().getUser());
        assertEquals("can_view", request.getValue().getRelation());
        assertEquals("knowledge_asset:" + assetId, request.getValue().getObject());
        assertEquals(1, request.getValue().getContextualTuples().size());
        assertEquals("organization:" + organizationId,
                request.getValue().getContextualTuples().getFirst().getUser());
    }
}
