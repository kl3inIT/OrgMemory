package com.orgmemory.integrations.authorization.openfga;

import com.orgmemory.core.authorization.RelationshipTuple;
import com.orgmemory.core.authorization.RelationshipTuplePage;
import com.orgmemory.core.authorization.RelationshipTupleReconciliationPort;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientReadRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.configuration.ClientDeleteTuplesOptions;
import dev.openfga.sdk.api.configuration.ClientReadOptions;
import dev.openfga.sdk.api.model.WriteRequestDeletes;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class OpenFgaRelationshipTupleReconciliationAdapter
        implements RelationshipTupleReconciliationPort {

    private final OpenFgaClient client;
    private final String authorizationModelId;
    private final Duration requestTimeout;

    public OpenFgaRelationshipTupleReconciliationAdapter(
            OpenFgaClient client,
            String authorizationModelId,
            Duration requestTimeout) {
        this.client = Objects.requireNonNull(client, "client");
        this.authorizationModelId = requireAuthorizationModelId(authorizationModelId);
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    @Override
    public String policyVersion() {
        return authorizationModelId;
    }

    @Override
    public RelationshipTuplePage read(int pageSize, String continuationToken) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        var options = new ClientReadOptions().pageSize(pageSize);
        if (continuationToken != null && !continuationToken.isBlank()) {
            options.continuationToken(continuationToken.trim());
        }
        try {
            var response = client.read(new ClientReadRequest(), options)
                    .get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return RelationshipTuplePage.resolved(
                    response.getTuples().stream()
                            .map(tuple -> RelationshipTuple.of(
                                    tuple.getKey().getUser(),
                                    tuple.getKey().getRelation(),
                                    tuple.getKey().getObject()))
                            .toList(),
                    response.getContinuationToken(),
                    authorizationModelId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return RelationshipTuplePage.indeterminate(
                    "OPENFGA_READ_INTERRUPTED", authorizationModelId);
        } catch (TimeoutException exception) {
            return RelationshipTuplePage.indeterminate(
                    "OPENFGA_READ_TIMEOUT", authorizationModelId);
        } catch (FgaInvalidParameterException | ExecutionException | RuntimeException exception) {
            return RelationshipTuplePage.indeterminate(
                    "OPENFGA_READ_UNAVAILABLE", authorizationModelId);
        }
    }

    @Override
    public RelationshipTupleWriteResult delete(RelationshipTupleWriteRequest request) {
        Objects.requireNonNull(request, "request");
        var tuples = request.tuples().stream()
                .map(tuple -> new ClientTupleKeyWithoutCondition()
                        .user(tuple.user())
                        .relation(tuple.relation())
                        ._object(tuple.object()))
                .toList();
        var options = new ClientDeleteTuplesOptions()
                .onMissing(WriteRequestDeletes.OnMissingEnum.IGNORE);
        try {
            client.deleteTuples(tuples, options)
                    .get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return RelationshipTupleWriteResult.applied(authorizationModelId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return RelationshipTupleWriteResult.indeterminate(
                    "OPENFGA_DELETE_INTERRUPTED", authorizationModelId);
        } catch (TimeoutException exception) {
            return RelationshipTupleWriteResult.indeterminate(
                    "OPENFGA_DELETE_TIMEOUT", authorizationModelId);
        } catch (FgaInvalidParameterException | ExecutionException | RuntimeException exception) {
            return RelationshipTupleWriteResult.indeterminate(
                    "OPENFGA_DELETE_UNAVAILABLE", authorizationModelId);
        }
    }

    private static String requireAuthorizationModelId(String value) {
        String normalized = Objects.requireNonNull(value, "authorizationModelId").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("authorizationModelId must not be blank");
        }
        return normalized;
    }
}
