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
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.api.configuration.ClientWriteOptions;
import dev.openfga.sdk.api.model.WriteRequestWrites;

import java.util.List;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenFgaRelationshipTupleWriteAdapterTests {

    @Test
    void writesIdempotentNounRelationshipsAgainstThePinnedModel() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        when(client.write(any(ClientWriteRequest.class), any(ClientWriteOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        var adapter = new OpenFgaRelationshipTupleWriteAdapter(client, "model-1", Duration.ofSeconds(1));

        var result = adapter.write(new RelationshipTupleWriteRequest(List.of(
                RelationshipTuple.of("user:123", "owner", "knowledge_asset:456"))));

        assertTrue(result.applied());
        var request = ArgumentCaptor.forClass(ClientWriteRequest.class);
        var options = ArgumentCaptor.forClass(ClientWriteOptions.class);
        verify(client).write(request.capture(), options.capture());
        assertEquals("user:123", request.getValue().getWrites().getFirst().getUser());
        assertEquals("owner", request.getValue().getWrites().getFirst().getRelation());
        assertEquals("knowledge_asset:456", request.getValue().getWrites().getFirst().getObject());
        assertEquals("model-1", options.getValue().getAuthorizationModelId());
        assertEquals(WriteRequestWrites.OnDuplicateEnum.IGNORE, options.getValue().getOnDuplicate());
    }
}
