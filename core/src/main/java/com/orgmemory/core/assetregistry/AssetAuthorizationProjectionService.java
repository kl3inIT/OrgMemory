package com.orgmemory.core.assetregistry;

import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class AssetAuthorizationProjectionService {

    private final AssetAuthorizationCoordinator coordinator;
    private final RelationshipTupleWritePort relationshipTuples;

    AssetAuthorizationProjectionService(
            AssetAuthorizationCoordinator coordinator,
            RelationshipTupleWritePort relationshipTuples) {
        this.coordinator = coordinator;
        this.relationshipTuples = relationshipTuples;
    }

    void project(UUID organizationId, UUID assetId) {
        AssetAuthorizationBatch batch = coordinator.startAttempt(organizationId, assetId);
        if (batch.tuples().isEmpty()) {
            throw new AssetUnavailableException(
                    "Asset authorization is already being projected");
        }
        project(batch);
    }

    void project(AssetAuthorizationBatch batch) {
        RelationshipTupleWriteResult result;
        try {
            result = Objects.requireNonNull(
                    relationshipTuples.write(new RelationshipTupleWriteRequest(batch.tuples())),
                    "relationship tuple write result");
        } catch (RuntimeException exception) {
            coordinator.recordFailure(
                    batch,
                    "OPENFGA_WRITE_FAILED",
                    "The Asset authorization relationship could not be applied");
            throw new AssetUnavailableException(
                    "Asset authorization is waiting for projection", exception);
        }
        if (!result.applied()) {
            coordinator.recordFailure(
                    batch,
                    result.reasonCode(),
                    "The Asset authorization relationship could not be confirmed");
            throw new AssetUnavailableException(
                    "Asset authorization is waiting for projection");
        }
        coordinator.complete(batch, result.policyVersion());
    }
}
