package com.orgmemory.integrations.authorization.openfga;

import com.orgmemory.core.authorization.ExpansionNode;
import com.orgmemory.core.authorization.RelationshipExpansionPort;
import com.orgmemory.core.authorization.RelationshipExpansionQuery;
import com.orgmemory.core.authorization.RelationshipExpansionResult;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientExpandRequest;
import dev.openfga.sdk.api.model.Computed;
import dev.openfga.sdk.api.model.Leaf;
import dev.openfga.sdk.api.model.Node;
import dev.openfga.sdk.api.model.Nodes;
import dev.openfga.sdk.api.model.UsersetTree;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OpenFgaRelationshipExpansionAdapter implements RelationshipExpansionPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenFgaRelationshipExpansionAdapter.class);

    private final OpenFgaClient client;
    private final String authorizationModelId;
    private final Duration requestTimeout;

    public OpenFgaRelationshipExpansionAdapter(
            OpenFgaClient client,
            String authorizationModelId,
            Duration requestTimeout) {
        this.client = Objects.requireNonNull(client, "client");
        this.authorizationModelId = requireModelId(authorizationModelId);
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    @Override
    public RelationshipExpansionResult expand(RelationshipExpansionQuery query) {
        Objects.requireNonNull(query, "query");
        var request = new ClientExpandRequest()
                ._object(query.resource().openFgaObject())
                .relation(query.relation().value());
        try {
            var response = client.expand(request).get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return RelationshipExpansionResult.resolved(root(response.getTree()), authorizationModelId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return RelationshipExpansionResult.indeterminate("OPENFGA_INTERRUPTED", authorizationModelId);
        } catch (TimeoutException exception) {
            return RelationshipExpansionResult.indeterminate("OPENFGA_TIMEOUT", authorizationModelId);
        } catch (FgaInvalidParameterException | ExecutionException | RuntimeException exception) {
            LOGGER.warn(
                    "OpenFGA Expand failed for object {} and relation {} using model {}",
                    query.resource().openFgaObject(),
                    query.relation().value(),
                    authorizationModelId,
                    exception);
            return RelationshipExpansionResult.indeterminate("OPENFGA_UNAVAILABLE", authorizationModelId);
        }
    }

    private static ExpansionNode root(UsersetTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("OpenFGA returned an expansion with no tree");
        }
        return node(tree.getRoot());
    }

    private static ExpansionNode node(Node node) {
        if (node == null) {
            throw new IllegalArgumentException("OpenFGA returned an empty expansion node");
        }
        String name = node.getName();
        if (node.getLeaf() != null) {
            return leaf(name, node.getLeaf());
        }
        if (node.getUnion() != null) {
            return new ExpansionNode.Union(name, children(node.getUnion()));
        }
        if (node.getIntersection() != null) {
            return new ExpansionNode.Intersection(name, children(node.getIntersection()));
        }
        if (node.getDifference() != null) {
            var difference = node.getDifference();
            return new ExpansionNode.Difference(
                    name, node(difference.getBase()), node(difference.getSubtract()));
        }
        throw new IllegalArgumentException("OpenFGA returned an expansion node with no resolvable branch");
    }

    private static ExpansionNode leaf(String name, Leaf leaf) {
        if (leaf.getUsers() != null) {
            List<String> users = leaf.getUsers().getUsers();
            return new ExpansionNode.Direct(name, users == null ? List.of() : users);
        }
        if (leaf.getComputed() != null) {
            return new ExpansionNode.Computed(name, leaf.getComputed().getUserset());
        }
        if (leaf.getTupleToUserset() != null) {
            var tupleToUserset = leaf.getTupleToUserset();
            List<Computed> computed = tupleToUserset.getComputed();
            return new ExpansionNode.TupleToUserset(
                    name,
                    tupleToUserset.getTupleset(),
                    computed == null
                            ? List.of()
                            : computed.stream()
                                    .map(Computed::getUserset)
                                    .filter(Objects::nonNull)
                                    .toList());
        }
        throw new IllegalArgumentException(
                "OpenFGA returned a leaf with no users, computed userset, or tupleset");
    }

    private static List<ExpansionNode> children(Nodes nodes) {
        List<Node> items = nodes.getNodes();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("OpenFGA returned a composed node with no children");
        }
        return items.stream().map(OpenFgaRelationshipExpansionAdapter::node).toList();
    }

    private static String requireModelId(String value) {
        String normalized = Objects.requireNonNull(value, "authorizationModelId").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("authorizationModelId must not be blank");
        }
        return normalized;
    }
}
