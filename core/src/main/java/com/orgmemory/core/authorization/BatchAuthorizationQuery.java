package com.orgmemory.core.authorization;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record BatchAuthorizationQuery(
        UUID organizationId,
        PrincipalRef principal,
        PermissionKey permission,
        List<ResourceRef> resources,
        Map<ResourceRef, List<ContextualRelationship>> contextualRelationships) {

    public BatchAuthorizationQuery(
            UUID organizationId,
            PrincipalRef principal,
            PermissionKey permission,
            List<ResourceRef> resources) {
        this(organizationId, principal, permission, resources, Map.of());
    }

    public BatchAuthorizationQuery {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(permission, "permission");
        resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("At least one resource is required");
        }
        var requestedResources = new HashSet<>(resources);
        if (requestedResources.size() != resources.size()) {
            throw new IllegalArgumentException("Batch resources must be unique");
        }
        Map<ResourceRef, List<ContextualRelationship>> requestedContext = Objects.requireNonNull(
                contextualRelationships, "contextualRelationships");
        Map<ResourceRef, List<ContextualRelationship>> normalizedContext = new LinkedHashMap<>();
        for (ResourceRef resource : resources) {
            if (!organizationId.equals(resource.organizationId())) {
                throw new IllegalArgumentException("Every resource must belong to the query organization");
            }
        }
        for (var entry : requestedContext.entrySet()) {
            if (!requestedResources.contains(entry.getKey())) {
                throw new IllegalArgumentException("Contextual relationships require a requested resource");
            }
            if (!organizationId.equals(entry.getKey().organizationId())) {
                throw new IllegalArgumentException("Contextual relationships cannot cross organizations");
            }
            normalizedContext.put(
                    entry.getKey(),
                    List.copyOf(Objects.requireNonNull(
                            entry.getValue(), "contextual relationship list")));
        }
        contextualRelationships = Map.copyOf(normalizedContext);
    }

    public List<ContextualRelationship> contextualRelationshipsFor(ResourceRef resource) {
        return contextualRelationships.getOrDefault(resource, List.of());
    }
}
