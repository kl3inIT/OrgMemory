package com.orgmemory.core.knowledge;

import com.orgmemory.core.authorization.AuthorizedResourceQuery;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeSpaceService {

    private static final String RESOURCE_TYPE = "knowledge_space";
    private static final PermissionKey CAN_CREATE_ASSET = PermissionKey.of("can_create_asset");

    private final KnowledgeSpaceRepository spaces;
    private final RelationshipAuthorizationPort authorization;
    private final RelationshipAuthorizationSetPort authorizationSets;

    KnowledgeSpaceService(
            KnowledgeSpaceRepository spaces,
            RelationshipAuthorizationPort authorization,
            RelationshipAuthorizationSetPort authorizationSets) {
        this.spaces = spaces;
        this.authorization = authorization;
        this.authorizationSets = authorizationSets;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSpaceTarget> listUploadTargets(CurrentActor actor) {
        Objects.requireNonNull(actor, "actor");
        var result = authorizationSets.listAuthorizedResources(new AuthorizedResourceQuery(
                actor.organizationId(),
                actor.principal(),
                CAN_CREATE_ASSET,
                RESOURCE_TYPE));
        if (!result.resolved()) {
            throw new KnowledgeSpaceUnavailableException(
                    "Knowledge Space permissions are temporarily unavailable");
        }

        Set<UUID> authorizedIds = new LinkedHashSet<>();
        try {
            for (ResourceRef resource : result.resources()) {
                if (!actor.organizationId().equals(resource.organizationId())
                        || !RESOURCE_TYPE.equals(resource.type())) {
                    throw new IllegalArgumentException("Unexpected authorization resource");
                }
                authorizedIds.add(UUID.fromString(resource.id()));
            }
        } catch (IllegalArgumentException invalidProjection) {
            throw new KnowledgeSpaceUnavailableException(
                    "Knowledge Space permissions are inconsistent with the directory");
        }
        if (authorizedIds.isEmpty()) {
            return List.of();
        }
        return spaces.findByOrganizationIdAndIdInAndActiveTrueOrderByName(
                        actor.organizationId(), authorizedIds)
                .stream()
                .map(KnowledgeSpaceService::target)
                .toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeSpaceTarget requireUploadTarget(CurrentActor actor, UUID knowledgeSpaceId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        KnowledgeSpace space = spaces.findByIdAndOrganizationIdAndActiveTrue(
                        knowledgeSpaceId, actor.organizationId())
                .orElseThrow(KnowledgeSpaceService::accessDenied);
        var decision = authorization.check(new RelationshipAuthorizationQuery(
                actor.principal(),
                CAN_CREATE_ASSET,
                ResourceRef.of(actor.organizationId(), RESOURCE_TYPE, space.getId())));
        if (!decision.allowed()) {
            throw accessDenied();
        }
        return target(space);
    }

    @Transactional(readOnly = true)
    void requireInOrganization(UUID organizationId, UUID knowledgeSpaceId) {
        if (!spaces.existsByIdAndOrganizationIdAndActiveTrue(knowledgeSpaceId, organizationId)) {
            throw new IllegalArgumentException(
                    "Knowledge Space does not belong to the organization");
        }
    }

    private static KnowledgeSpaceTarget target(KnowledgeSpace space) {
        return new KnowledgeSpaceTarget(
                space.getId(),
                space.getKey(),
                space.getName(),
                space.getDepartmentId());
    }

    private static OrgMemoryAccessDeniedException accessDenied() {
        return new OrgMemoryAccessDeniedException(
                "The current user is not authorized to add knowledge to this space");
    }
}
