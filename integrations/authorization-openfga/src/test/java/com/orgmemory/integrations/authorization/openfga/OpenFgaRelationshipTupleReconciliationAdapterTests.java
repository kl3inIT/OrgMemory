package com.orgmemory.integrations.authorization.openfga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.RelationshipTuple;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientReadRequest;
import dev.openfga.sdk.api.client.model.ClientReadResponse;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.client.model.ClientWriteResponse;
import dev.openfga.sdk.api.configuration.ClientDeleteTuplesOptions;
import dev.openfga.sdk.api.configuration.ClientReadOptions;
import dev.openfga.sdk.api.model.Tuple;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.api.model.WriteRequestDeletes;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenFgaRelationshipTupleReconciliationAdapterTests {

    @Test
    void readsPagedTuplesAndDeletesIdempotently() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        ClientReadResponse response = mock(ClientReadResponse.class);
        when(response.getTuples()).thenReturn(List.of(new Tuple().key(new TupleKey()
                .user("user:123")
                .relation("owner")
                ._object("knowledge_asset:456"))));
        when(response.getContinuationToken()).thenReturn("next-page");
        when(client.read(any(ClientReadRequest.class), any(ClientReadOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        when(client.deleteTuples(
                        any(),
                        any(ClientDeleteTuplesOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));
        var adapter = new OpenFgaRelationshipTupleReconciliationAdapter(
                client, "model-2", Duration.ofSeconds(1));

        var page = adapter.read(100, "previous-page");
        var result = adapter.delete(new RelationshipTupleWriteRequest(List.of(
                RelationshipTuple.of("user:123", "owner", "knowledge_asset:456"))));

        assertTrue(page.resolved());
        assertEquals("next-page", page.continuationToken());
        assertEquals("knowledge_asset:456", page.tuples().getFirst().object());
        assertTrue(result.applied());

        var readOptions = ArgumentCaptor.forClass(ClientReadOptions.class);
        verify(client).read(any(ClientReadRequest.class), readOptions.capture());
        assertEquals(100, readOptions.getValue().getPageSize());
        assertEquals("previous-page", readOptions.getValue().getContinuationToken());

        @SuppressWarnings("unchecked")
        var tuples = ArgumentCaptor.forClass((Class<List<ClientTupleKeyWithoutCondition>>) (Class<?>) List.class);
        var deleteOptions = ArgumentCaptor.forClass(ClientDeleteTuplesOptions.class);
        verify(client).deleteTuples(tuples.capture(), deleteOptions.capture());
        assertEquals("knowledge_asset:456", tuples.getValue().getFirst().getObject());
        assertEquals(
                WriteRequestDeletes.OnMissingEnum.IGNORE,
                deleteOptions.getValue().getOnMissing());
    }
}
