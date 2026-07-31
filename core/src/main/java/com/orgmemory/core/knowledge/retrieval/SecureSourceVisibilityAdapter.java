package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.authorization.AuthorizedResourceQuery;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.sourceledger.SourceVisibilityPort;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.organization.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Retrieval-owned authorization adapter for source listings. */
@Service
class SecureSourceVisibilityAdapter implements SourceVisibilityPort {

    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final String RESOURCE_TYPE = "knowledge_asset";

    private final AppUserRepository users;
    private final RelationshipAuthorizationSetPort authorization;
    private final SecureKnowledgeRetrievalStore visibility;
    private final KnowledgeRetrievalProperties properties;

    SecureSourceVisibilityAdapter(
            AppUserRepository users,
            RelationshipAuthorizationSetPort authorization,
            SecureKnowledgeRetrievalStore visibility,
            KnowledgeRetrievalProperties properties) {
        this.users = users;
        this.authorization = authorization;
        this.visibility = visibility;
        this.properties = properties;
    }

    @Override
    public List<UUID> visibleSourceObjectIds(CurrentActor actor) {
        AppUser subject = users.findById(actor.userId())
                .filter(user -> user.getOrganizationId().equals(actor.organizationId())
                        && user.isActive())
                .orElseThrow(() -> new OrgMemoryAccessDeniedException(
                        "Knowledge access profile is incomplete"));
        var listed = authorization.listAuthorizedResources(new AuthorizedResourceQuery(
                actor.organizationId(), actor.principal(), CAN_VIEW, RESOURCE_TYPE));
        if (!listed.resolved()) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Document permissions are temporarily unavailable");
        }
        List<ResourceRef> resources = listed.resources().stream()
                .filter(resource -> actor.organizationId().equals(resource.organizationId())
                        && RESOURCE_TYPE.equals(resource.type()))
                .distinct()
                .toList();
        if (resources.size() != listed.resources().size()
                || resources.size() > properties.maximumAuthorizedObjects()) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Document permissions are inconsistent");
        }
        if (resources.isEmpty()) {
            return List.of();
        }
        List<UUID> assetIds;
        try {
            assetIds = resources.stream()
                    .map(resource -> UUID.fromString(resource.id()))
                    .toList();
        } catch (IllegalArgumentException invalidResource) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Document permissions are inconsistent");
        }
        return visibility.visibleSourceObjectIds(new SecureKnowledgeRetrievalStore.RetrievalScope(
                actor.organizationId(),
                actor.userId(),
                subject.getDepartmentId(),
                subject.getRole() == UserRole.EXECUTIVE,
                assetIds,
                listed.policyVersion(),
                Instant.now()));
    }
}
