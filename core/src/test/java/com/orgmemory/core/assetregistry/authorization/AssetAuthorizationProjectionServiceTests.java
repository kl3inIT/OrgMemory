package com.orgmemory.core.assetregistry.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.assetregistry.kernel.AssetAuthorizationBatch;
import com.orgmemory.core.assetregistry.kernel.AssetAuthorizationProjectionQueue;
import com.orgmemory.core.authorization.RelationshipTuple;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class AssetAuthorizationProjectionServiceTests {

    @Test
    void completesTheClaimOnlyAfterTheRelationshipWriteIsConfirmed() {
        AssetAuthorizationProjectionQueue queue = mock(AssetAuthorizationProjectionQueue.class);
        RelationshipTupleWritePort writer = mock(RelationshipTupleWritePort.class);
        AssetAuthorizationBatch batch = mock(AssetAuthorizationBatch.class);
        UUID organizationId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(batch.tuples()).thenReturn(List.of(RelationshipTuple.of(
                "user:" + UUID.randomUUID(), "owner", "asset:" + assetId)));
        when(queue.claimForAsset(organizationId, assetId)).thenReturn(Optional.of(batch));
        when(writer.write(any(RelationshipTupleWriteRequest.class)))
                .thenReturn(RelationshipTupleWriteResult.applied("model-1"));
        AssetAuthorizationProjectionService service =
                new AssetAuthorizationProjectionService(queue, writer);

        service.project(organizationId, assetId);

        verify(queue).complete(batch, "model-1");
    }

    @Test
    void recordsAnOpaqueFailureWhenTheExternalWriterThrows() {
        AssetAuthorizationProjectionQueue queue = mock(AssetAuthorizationProjectionQueue.class);
        RelationshipTupleWritePort writer = mock(RelationshipTupleWritePort.class);
        AssetAuthorizationBatch batch = mock(AssetAuthorizationBatch.class);
        UUID organizationId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(batch.tuples()).thenReturn(List.of(RelationshipTuple.of(
                "user:" + UUID.randomUUID(), "owner", "asset:" + assetId)));
        when(queue.claimForAsset(organizationId, assetId)).thenReturn(Optional.of(batch));
        when(writer.write(any(RelationshipTupleWriteRequest.class)))
                .thenThrow(new IllegalStateException("provider detail"));
        AssetAuthorizationProjectionService service =
                new AssetAuthorizationProjectionService(queue, writer);

        assertThrows(
                AssetUnavailableException.class,
                () -> service.project(organizationId, assetId));

        verify(queue).fail(
                batch,
                "OPENFGA_WRITE_FAILED",
                "The Asset authorization relationship could not be applied");
    }

    @Test
    void externalProjectionExplicitlyRejectsAnAmbientDatabaseTransaction()
            throws NoSuchMethodException {
        Method projection = AssetAuthorizationProjectionService.class.getMethod(
                "project", UUID.class, UUID.class);
        Method convergence = AssetAuthorizationConvergenceService.class.getMethod(
                "reconcile", int.class);

        assertEquals(
                Propagation.NEVER,
                projection.getAnnotation(Transactional.class).propagation());
        assertEquals(
                Propagation.NEVER,
                convergence.getAnnotation(Transactional.class).propagation());
    }
}
