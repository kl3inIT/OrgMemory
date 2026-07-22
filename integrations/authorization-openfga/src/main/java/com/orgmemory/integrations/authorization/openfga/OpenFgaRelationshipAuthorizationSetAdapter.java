package com.orgmemory.integrations.authorization.openfga;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.AuthorizedResourceQuery;
import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.BatchAuthorizationResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientBatchCheckItem;
import dev.openfga.sdk.api.client.model.ClientBatchCheckRequest;
import dev.openfga.sdk.api.client.model.ClientListObjectsRequest;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import dev.openfga.sdk.errors.FgaValidationError;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class OpenFgaRelationshipAuthorizationSetAdapter implements RelationshipAuthorizationSetPort {

    private final OpenFgaClient client;
    private final String authorizationModelId;
    private final Duration requestTimeout;

    public OpenFgaRelationshipAuthorizationSetAdapter(
            OpenFgaClient client,
            String authorizationModelId,
            Duration requestTimeout) {
        this.client = Objects.requireNonNull(client, "client");
        this.authorizationModelId = requireModelId(authorizationModelId);
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    @Override
    public AuthorizedResourceSetResult listAuthorizedResources(AuthorizedResourceQuery query) {
        Objects.requireNonNull(query, "query");
        var request = new ClientListObjectsRequest()
                .user(query.principal().openFgaUser())
                .relation(query.permission().value())
                .type(query.resourceType());
        try {
            var response = client.listObjects(request).get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
            List<ResourceRef> resources = response.getObjects().stream()
                    .map(reference -> resource(query, reference))
                    .toList();
            return AuthorizedResourceSetResult.resolved(resources, authorizationModelId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return AuthorizedResourceSetResult.indeterminate("OPENFGA_INTERRUPTED", authorizationModelId);
        } catch (TimeoutException exception) {
            return AuthorizedResourceSetResult.indeterminate("OPENFGA_TIMEOUT", authorizationModelId);
        } catch (FgaInvalidParameterException | ExecutionException | RuntimeException exception) {
            return AuthorizedResourceSetResult.indeterminate("OPENFGA_UNAVAILABLE", authorizationModelId);
        }
    }

    @Override
    public BatchAuthorizationResult batchCheck(BatchAuthorizationQuery query) {
        Objects.requireNonNull(query, "query");
        var request = ClientBatchCheckRequest.ofChecks(query.resources().stream()
                .map(resource -> new ClientBatchCheckItem()
                        .user(query.principal().openFgaUser())
                        .relation(query.permission().value())
                        ._object(resource.openFgaObject())
                        .correlationId(resource.openFgaObject()))
                .toList());
        try {
            var response = client.batchCheck(request).get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
            Map<String, ResourceRef> requested = new LinkedHashMap<>();
            query.resources().forEach(resource -> requested.put(resource.openFgaObject(), resource));
            Map<ResourceRef, AuthorizationDecision> decisions = new LinkedHashMap<>();
            for (var item : response.getResult()) {
                ResourceRef resource = requested.remove(item.getCorrelationId());
                if (resource == null || item.getError() != null) {
                    return BatchAuthorizationResult.indeterminate(
                            "OPENFGA_BATCH_INCOMPLETE", authorizationModelId);
                }
                decisions.put(
                        resource,
                        item.isAllowed()
                                ? AuthorizationDecision.allow(authorizationModelId)
                                : AuthorizationDecision.deny("RELATIONSHIP_DENIED", authorizationModelId));
            }
            if (!requested.isEmpty() || decisions.size() != query.resources().size()) {
                return BatchAuthorizationResult.indeterminate(
                        "OPENFGA_BATCH_INCOMPLETE", authorizationModelId);
            }
            return BatchAuthorizationResult.resolved(decisions, authorizationModelId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return BatchAuthorizationResult.indeterminate("OPENFGA_INTERRUPTED", authorizationModelId);
        } catch (TimeoutException exception) {
            return BatchAuthorizationResult.indeterminate("OPENFGA_TIMEOUT", authorizationModelId);
        } catch (FgaInvalidParameterException | FgaValidationError | ExecutionException | RuntimeException exception) {
            return BatchAuthorizationResult.indeterminate("OPENFGA_UNAVAILABLE", authorizationModelId);
        }
    }

    private static ResourceRef resource(AuthorizedResourceQuery query, String reference) {
        String prefix = query.resourceType() + ":";
        if (reference == null || !reference.startsWith(prefix) || reference.length() == prefix.length()) {
            throw new IllegalArgumentException("OpenFGA returned an invalid object reference");
        }
        return new ResourceRef(
                query.organizationId(),
                query.resourceType(),
                reference.substring(prefix.length()));
    }

    private static String requireModelId(String value) {
        String normalized = Objects.requireNonNull(value, "authorizationModelId").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("authorizationModelId must not be blank");
        }
        return normalized;
    }
}
