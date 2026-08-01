package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException;

import com.orgmemory.core.knowledge.asset.KnowledgeAssetRepository;

import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceQuery;
import com.orgmemory.core.organization.DepartmentRepository;
import com.orgmemory.core.organization.OrganizationRepository;
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

    private final OrganizationRepository organizations;
    private final DepartmentRepository departments;
    private final KnowledgeSpaceQuery spaces;
    private final KnowledgeAssetRepository assets;

    AuthorizationResourceDirectory(
            OrganizationRepository organizations,
            DepartmentRepository departments,
            KnowledgeSpaceQuery spaces,
            KnowledgeAssetRepository assets) {
        this.organizations = organizations;
        this.departments = departments;
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
                            && organizations.existsById(organizationId);
            case "organizational_unit" ->
                    departments.existsByIdAndOrganizationId(resourceId, organizationId);
            case "knowledge_space" ->
                    spaces.exists(organizationId, resourceId);
            case "knowledge_asset" ->
                    assets.existsByIdAndOrganizationId(resourceId, organizationId);
            default -> false;
        };
        if (!exists) {
            throw new KnowledgeResourceNotFoundException();
        }
        return ResourceRef.of(organizationId, type, resourceId);
    }
}
