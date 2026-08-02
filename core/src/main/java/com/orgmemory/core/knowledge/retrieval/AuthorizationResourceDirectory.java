package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException;

import com.orgmemory.core.knowledge.asset.KnowledgeAssetRetrievalQuery;

import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceQuery;
import com.orgmemory.core.organization.OrganizationResourceQuery;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves an administrator-supplied authorization resource against the
 * canonical tenant directory before OpenFGA is queried.
 */
@Service
public class AuthorizationResourceDirectory {

    private final OrganizationResourceQuery organizationResources;
    private final KnowledgeSpaceQuery spaces;
    private final KnowledgeAssetRetrievalQuery assets;

    AuthorizationResourceDirectory(
            OrganizationResourceQuery organizationResources,
            KnowledgeSpaceQuery spaces,
            KnowledgeAssetRetrievalQuery assets) {
        this.organizationResources = organizationResources;
        this.spaces = spaces;
        this.assets = assets;
    }

    @Transactional(readOnly = true)
    public ResourceRef require(
            UUID organizationId,
            String resourceType,
            UUID resourceId) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(resourceId, "resourceId");
        String type = Objects.requireNonNull(resourceType, "resourceType").strip();
        boolean exists = switch (type) {
            case "organization" ->
                    organizationId.equals(resourceId)
                            && organizationResources.organizationExists(organizationId);
            case "organizational_unit" ->
                    organizationResources.departmentExists(organizationId, resourceId);
            case "knowledge_space" ->
                    spaces.exists(organizationId, resourceId);
            case "knowledge_asset" ->
                    assets.exists(organizationId, resourceId);
            default -> false;
        };
        if (!exists) {
            throw new KnowledgeResourceNotFoundException();
        }
        return ResourceRef.of(organizationId, type, resourceId);
    }
}
