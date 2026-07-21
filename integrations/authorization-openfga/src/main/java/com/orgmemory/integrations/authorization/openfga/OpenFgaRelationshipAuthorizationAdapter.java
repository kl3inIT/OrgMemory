package com.orgmemory.integrations.authorization.openfga;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public final class OpenFgaRelationshipAuthorizationAdapter implements RelationshipAuthorizationPort {

    private final OpenFgaClient client;
    private final String authorizationModelId;

    public OpenFgaRelationshipAuthorizationAdapter(OpenFgaClient client, String authorizationModelId) {
        this.client = Objects.requireNonNull(client, "client");
        this.authorizationModelId = requireText(authorizationModelId, "authorizationModelId");
    }

    @Override
    public AuthorizationDecision check(RelationshipAuthorizationQuery query) {
        Objects.requireNonNull(query, "query");
        var request = new ClientCheckRequest()
                .user(query.principal().openFgaUser())
                .relation(query.permission().value())
                ._object(query.resource().openFgaObject())
                .contextualTuples(query.contextualRelationships().stream()
                        .map(relationship -> new ClientTupleKey()
                                .user(relationship.user())
                                .relation(relationship.relation())
                                ._object(relationship.object()))
                        .toList());
        try {
            var response = client.check(request).get();
            return Boolean.TRUE.equals(response.getAllowed())
                    ? AuthorizationDecision.allow(authorizationModelId)
                    : AuthorizationDecision.deny("RELATIONSHIP_DENIED", authorizationModelId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return AuthorizationDecision.indeterminate("OPENFGA_INTERRUPTED", authorizationModelId);
        } catch (FgaInvalidParameterException | ExecutionException | RuntimeException exception) {
            return AuthorizationDecision.indeterminate("OPENFGA_UNAVAILABLE", authorizationModelId);
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
