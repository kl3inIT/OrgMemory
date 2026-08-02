package com.orgmemory.core.assetregistry.authorization;

import com.orgmemory.core.assetregistry.api.AssetAuthorizationProjectionCommand;
import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.assetregistry.kernel.AssetAuthorizationBatch;
import com.orgmemory.core.assetregistry.kernel.AssetAuthorizationProjectionQueue;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssetAuthorizationProjectionService implements AssetAuthorizationProjectionCommand {

    private static final Logger log =
            LoggerFactory.getLogger(AssetAuthorizationProjectionService.class);

    private final AssetAuthorizationProjectionQueue queue;
    private final RelationshipTupleWritePort relationshipTuples;

    AssetAuthorizationProjectionService(
            AssetAuthorizationProjectionQueue queue,
            RelationshipTupleWritePort relationshipTuples) {
        this.queue = queue;
        this.relationshipTuples = relationshipTuples;
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void project(UUID organizationId, UUID assetId) {
        AssetAuthorizationBatch batch = queue.claimForAsset(organizationId, assetId)
                .orElseThrow(() -> new AssetUnavailableException(
                        "Asset authorization is already being projected"));
        project(batch);
    }

    @Transactional(propagation = Propagation.NEVER)
    void project(AssetAuthorizationBatch batch) {
        RelationshipTupleWriteResult result;
        try {
            result = Objects.requireNonNull(
                    relationshipTuples.write(new RelationshipTupleWriteRequest(batch.tuples())),
                    "relationship tuple write result");
        } catch (RuntimeException exception) {
            log.warn(
                    "Asset authorization projection failed for organization {} and asset {}",
                    batch.organizationId(),
                    batch.assetId(),
                    exception);
            queue.fail(
                    batch,
                    "OPENFGA_WRITE_FAILED",
                    "The Asset authorization relationship could not be applied");
            throw new AssetUnavailableException(
                    "Asset authorization is waiting for projection", exception);
        }
        if (!result.applied()) {
            queue.fail(
                    batch,
                    result.reasonCode(),
                    "The Asset authorization relationship could not be confirmed");
            throw new AssetUnavailableException(
                    "Asset authorization is waiting for projection");
        }
        queue.complete(batch, result.policyVersion());
    }
}
