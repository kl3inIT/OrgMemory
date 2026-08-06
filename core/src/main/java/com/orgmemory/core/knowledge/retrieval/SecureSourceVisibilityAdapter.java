package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.authorization.AuthorizedResourceQuery;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.sourceledger.SourceVisibilityPort;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.KnowledgeAccessSubject;
import com.orgmemory.core.organization.KnowledgeAccessSubjectQuery;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Retrieval-owned authorization adapter for source listings. */
@Service
class SecureSourceVisibilityAdapter implements SourceVisibilityPort {

    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final String RESOURCE_TYPE = "knowledge_asset";

    private final KnowledgeAccessSubjectQuery subjects;
    private final RelationshipAuthorizationSetPort authorization;
    private final SecureKnowledgeRetrievalStore visibility;
    private final KnowledgeRetrievalProperties properties;

    SecureSourceVisibilityAdapter(
            KnowledgeAccessSubjectQuery subjects,
            RelationshipAuthorizationSetPort authorization,
            SecureKnowledgeRetrievalStore visibility,
            KnowledgeRetrievalProperties properties) {
        this.subjects = subjects;
        this.authorization = authorization;
        this.visibility = visibility;
        this.properties = properties;
    }

    @Override
    public List<UUID> visibleSourceObjectIds(CurrentActor actor) {
        KnowledgeAccessSubject subject = subjects.findActive(
                        actor.organizationId(), actor.userId())
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
                subject.departmentId(),
                subject.executive(),
                assetIds,
                listed.policyVersion(),
                Instant.now()));
    }

    @Override
    public int maximumAuthorizedObjects() {
        return properties.maximumAuthorizedObjects();
    }

    @Override
    public void requireWithinMaximumAuthorizedObjects(int sourceObjectCount) {
        if (sourceObjectCount > properties.maximumAuthorizedObjects()) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Document permissions are inconsistent");
        }
    }
}
