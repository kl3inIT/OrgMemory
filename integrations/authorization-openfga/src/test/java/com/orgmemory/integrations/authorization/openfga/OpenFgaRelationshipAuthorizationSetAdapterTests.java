package com.orgmemory.integrations.authorization.openfga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizedResourceQuery;
import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.ContextualRelationship;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.PrincipalRef;
import com.orgmemory.core.authorization.ResourceRef;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientBatchCheckItem;
import dev.openfga.sdk.api.client.model.ClientBatchCheckRequest;
import dev.openfga.sdk.api.client.model.ClientBatchCheckResponse;
import dev.openfga.sdk.api.client.model.ClientBatchCheckSingleResponse;
import dev.openfga.sdk.api.client.model.ClientListObjectsRequest;
import dev.openfga.sdk.api.client.model.ClientListObjectsResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenFgaRelationshipAuthorizationSetAdapterTests {

    @Test
    void listObjectsMapsOnlyValidResourceReferences() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        ClientListObjectsResponse response = mock(ClientListObjectsResponse.class);
        UUID assetId = UUID.randomUUID();
        when(response.getObjects()).thenReturn(List.of("knowledge_asset:" + assetId));
        when(client.listObjects(any(ClientListObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        UUID organizationId = UUID.randomUUID();

        var result = adapter(client).listAuthorizedResources(new AuthorizedResourceQuery(
                organizationId,
                PrincipalRef.user(UUID.randomUUID()),
                PermissionKey.of("can_view"),
                "knowledge_asset"));

        assertTrue(result.resolved());
        assertEquals(List.of(ResourceRef.of(organizationId, "knowledge_asset", assetId)), result.resources());
        assertEquals("model-1", result.policyVersion());
    }

    @Test
    void malformedListObjectsResponseFailsClosed() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        ClientListObjectsResponse response = mock(ClientListObjectsResponse.class);
        when(response.getObjects()).thenReturn(List.of("other_type:not-allowed"));
        when(client.listObjects(any(ClientListObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        var result = adapter(client).listAuthorizedResources(new AuthorizedResourceQuery(
                UUID.randomUUID(),
                PrincipalRef.user(UUID.randomUUID()),
                PermissionKey.of("can_view"),
                "knowledge_asset"));

        assertFalse(result.resolved());
        assertTrue(result.resources().isEmpty());
    }

    @Test
    void batchCheckMapsAllowAndDenyAndRejectsPartialResponses() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        UUID organizationId = UUID.randomUUID();
        ResourceRef allowed = ResourceRef.of(organizationId, "knowledge_asset", UUID.randomUUID());
        ResourceRef denied = ResourceRef.of(organizationId, "knowledge_asset", UUID.randomUUID());
        ClientBatchCheckItem allowedItem = item(allowed, "0");
        ClientBatchCheckItem deniedItem = item(denied, "1");
        when(client.batchCheck(any(ClientBatchCheckRequest.class))).thenReturn(
                CompletableFuture.completedFuture(new ClientBatchCheckResponse(List.of(
                        new ClientBatchCheckSingleResponse(true, allowedItem, "0", null),
                        new ClientBatchCheckSingleResponse(false, deniedItem, "1", null)))));
        BatchAuthorizationQuery query = new BatchAuthorizationQuery(
                organizationId,
                PrincipalRef.user(UUID.randomUUID()),
                PermissionKey.of("can_view"),
                List.of(allowed, denied));

        var result = adapter(client).batchCheck(query);

        assertTrue(result.resolved());
        assertTrue(result.decisions().get(allowed).allowed());
        assertFalse(result.decisions().get(denied).allowed());

        when(client.batchCheck(any(ClientBatchCheckRequest.class))).thenReturn(
                CompletableFuture.completedFuture(new ClientBatchCheckResponse(List.of(
                        new ClientBatchCheckSingleResponse(true, allowedItem, "0", null)))));
        assertFalse(adapter(client).batchCheck(query).resolved());
    }

    @Test
    void batchCheckSendsResourceSpecificContextualRelationships() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        UUID organizationId = UUID.randomUUID();
        ResourceRef resource = ResourceRef.of(organizationId, "capability_asset", UUID.randomUUID());
        ClientBatchCheckItem responseItem = item(resource, "0");
        when(client.batchCheck(any(ClientBatchCheckRequest.class))).thenReturn(
                CompletableFuture.completedFuture(new ClientBatchCheckResponse(List.of(
                        new ClientBatchCheckSingleResponse(true, responseItem, "0", null)))));
        String contextualUser = "user:" + UUID.randomUUID();

        var result = adapter(client).batchCheck(new BatchAuthorizationQuery(
                organizationId,
                PrincipalRef.user(UUID.randomUUID()),
                PermissionKey.of("can_view"),
                List.of(resource),
                java.util.Map.of(resource, List.of(ContextualRelationship.of(
                        contextualUser, "owner", resource.openFgaObject())))));

        assertTrue(result.resolved());
        ArgumentCaptor<ClientBatchCheckRequest> request = ArgumentCaptor.forClass(ClientBatchCheckRequest.class);
        verify(client).batchCheck(request.capture());
        ClientBatchCheckItem sent = request.getValue().getChecks().getFirst();
        assertEquals(1, sent.getContextualTuples().size());
        assertEquals(contextualUser, sent.getContextualTuples().getFirst().getUser());
        assertEquals("owner", sent.getContextualTuples().getFirst().getRelation());
        assertEquals(resource.openFgaObject(), sent.getContextualTuples().getFirst().getObject());
    }

    @Test
    void providerTimeoutFailsClosed() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        when(client.listObjects(any(ClientListObjectsRequest.class))).thenReturn(new CompletableFuture<>());
        var adapter = new OpenFgaRelationshipAuthorizationSetAdapter(
                client, "model-1", Duration.ofMillis(1));

        var result = adapter.listAuthorizedResources(new AuthorizedResourceQuery(
                UUID.randomUUID(),
                PrincipalRef.user(UUID.randomUUID()),
                PermissionKey.of("can_view"),
                "knowledge_asset"));

        assertFalse(result.resolved());
        assertEquals("OPENFGA_TIMEOUT", result.reasonCode());
    }

    @Test
    void interruptionAndProviderFailureFailClosed() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        when(client.listObjects(any(ClientListObjectsRequest.class))).thenReturn(new CompletableFuture<>());
        var query = new AuthorizedResourceQuery(
                UUID.randomUUID(),
                PrincipalRef.user(UUID.randomUUID()),
                PermissionKey.of("can_view"),
                "knowledge_asset");

        try {
            Thread.currentThread().interrupt();
            var interrupted = adapter(client).listAuthorizedResources(query);
            assertFalse(interrupted.resolved());
            assertEquals("OPENFGA_INTERRUPTED", interrupted.reasonCode());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            assertTrue(Thread.interrupted());
        }

        CompletableFuture<ClientListObjectsResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("provider unavailable"));
        when(client.listObjects(any(ClientListObjectsRequest.class))).thenReturn(failed);
        var unavailable = adapter(client).listAuthorizedResources(query);

        assertFalse(unavailable.resolved());
        assertEquals("OPENFGA_UNAVAILABLE", unavailable.reasonCode());
    }

    private static OpenFgaRelationshipAuthorizationSetAdapter adapter(OpenFgaClient client) {
        return new OpenFgaRelationshipAuthorizationSetAdapter(client, "model-1", Duration.ofSeconds(1));
    }

    private static ClientBatchCheckItem item(ResourceRef resource, String correlationId) {
        return new ClientBatchCheckItem()
                .user("user:test")
                .relation("can_view")
                ._object(resource.openFgaObject())
                .correlationId(correlationId);
    }
}
