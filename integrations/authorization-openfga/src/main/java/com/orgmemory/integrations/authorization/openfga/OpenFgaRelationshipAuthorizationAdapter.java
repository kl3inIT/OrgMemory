package com.orgmemory.integrations.authorization.openfga;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OpenFgaRelationshipAuthorizationAdapter implements RelationshipAuthorizationPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenFgaRelationshipAuthorizationAdapter.class);

    private final OpenFgaClient client;
    private final String authorizationModelId;
    private final Duration requestTimeout;

    public OpenFgaRelationshipAuthorizationAdapter(
            OpenFgaClient client,
            String authorizationModelId,
            Duration requestTimeout) {
        this.client = Objects.requireNonNull(client, "client");
        this.authorizationModelId = requireModelId(authorizationModelId);
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
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
            var response = client.check(request).get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return Boolean.TRUE.equals(response.getAllowed())
                    ? AuthorizationDecision.allow(authorizationModelId)
                    : AuthorizationDecision.deny("RELATIONSHIP_DENIED", authorizationModelId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return AuthorizationDecision.indeterminate("OPENFGA_INTERRUPTED", authorizationModelId);
        } catch (TimeoutException exception) {
            return AuthorizationDecision.indeterminate("OPENFGA_TIMEOUT", authorizationModelId);
        } catch (FgaInvalidParameterException | ExecutionException | RuntimeException exception) {
            LOGGER.warn(
                    "OpenFGA Check failed for resource type {} and relation {} using model {}",
                    query.resource().type(),
                    query.permission().value(),
                    authorizationModelId,
                    exception);
            return AuthorizationDecision.indeterminate("OPENFGA_UNAVAILABLE", authorizationModelId);
        }
    }

    private static String requireModelId(String value) {
        String normalized = Objects.requireNonNull(value, "authorizationModelId").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("authorizationModelId must not be blank");
        }
        return normalized;
    }
}
