package com.orgmemory.integrations.authorization.openfga;

import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.api.configuration.ClientWriteOptions;
import dev.openfga.sdk.api.model.WriteRequestWrites;
import dev.openfga.sdk.errors.FgaInvalidParameterException;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

public final class OpenFgaRelationshipTupleWriteAdapter implements RelationshipTupleWritePort {

    private final OpenFgaClient client;
    private final String authorizationModelId;

    public OpenFgaRelationshipTupleWriteAdapter(OpenFgaClient client, String authorizationModelId) {
        this.client = Objects.requireNonNull(client, "client");
        this.authorizationModelId = requireAuthorizationModelId(authorizationModelId);
    }

    @Override
    public RelationshipTupleWriteResult write(RelationshipTupleWriteRequest request) {
        Objects.requireNonNull(request, "request");
        var write = new ClientWriteRequest().writes(request.tuples().stream()
                .map(tuple -> new ClientTupleKey()
                        .user(tuple.user())
                        .relation(tuple.relation())
                        ._object(tuple.object()))
                .toList());
        var options = new ClientWriteOptions()
                .authorizationModelId(authorizationModelId)
                .onDuplicate(WriteRequestWrites.OnDuplicateEnum.IGNORE);
        try {
            client.write(write, options).get();
            return RelationshipTupleWriteResult.applied(authorizationModelId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return RelationshipTupleWriteResult.indeterminate(
                    "OPENFGA_WRITE_INTERRUPTED", authorizationModelId);
        } catch (FgaInvalidParameterException | ExecutionException | RuntimeException exception) {
            return RelationshipTupleWriteResult.indeterminate(
                    "OPENFGA_WRITE_UNAVAILABLE", authorizationModelId);
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
