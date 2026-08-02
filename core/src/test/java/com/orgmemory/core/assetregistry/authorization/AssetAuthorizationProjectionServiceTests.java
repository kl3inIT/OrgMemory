package com.orgmemory.core.assetregistry.authorization;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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
    void rejectsAClaimThatIsAlreadyBeingProjected() {
        AssetAuthorizationProjectionQueue queue = mock(AssetAuthorizationProjectionQueue.class);
        UUID organizationId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(queue.claimForAsset(organizationId, assetId)).thenReturn(Optional.empty());
        AssetAuthorizationProjectionService service = new AssetAuthorizationProjectionService(
                queue, mock(RelationshipTupleWritePort.class));

        assertThrows(
                AssetUnavailableException.class,
                () -> service.project(organizationId, assetId));
    }

    @Test
    void externalProjectionRejectsAnAmbientDatabaseTransactionAtRuntime() {
        TestTransactionManager transactions = new TestTransactionManager();
        AssetAuthorizationProjectionQueue queue = mock(AssetAuthorizationProjectionQueue.class);
        AssetAuthorizationProjectionService projection = transactionalProxy(
                new AssetAuthorizationProjectionService(
                        queue, mock(RelationshipTupleWritePort.class)),
                AssetAuthorizationProjectionService.class,
                transactions);
        AssetAuthorizationConvergenceService convergence = transactionalProxy(
                new AssetAuthorizationConvergenceService(queue, projection),
                AssetAuthorizationConvergenceService.class,
                transactions);
        AssetAuthorizationBatch batch = mock(AssetAuthorizationBatch.class);
        TransactionTemplate outer = new TransactionTemplate(transactions);

        assertThrows(
                IllegalTransactionStateException.class,
                () -> outer.executeWithoutResult(status -> projection.project(batch)));
        assertThrows(
                IllegalTransactionStateException.class,
                () -> outer.executeWithoutResult(status -> convergence.reconcile(10)));
    }

    private static <T> T transactionalProxy(
            T target, Class<T> targetType, TestTransactionManager transactions) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource(false));
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return targetType.cast(factory.getProxy());
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

        private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return active.get();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            active.set(true);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // No resource commit is needed for transaction-boundary verification.
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // No resource rollback is needed for transaction-boundary verification.
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            active.remove();
        }
    }
}
