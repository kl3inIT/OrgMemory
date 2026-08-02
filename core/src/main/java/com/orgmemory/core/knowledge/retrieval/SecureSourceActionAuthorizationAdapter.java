package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.authorization.AuthorizedResourceQuery;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.knowledge.sourceledger.SourceActionAuthorizationPort;
import com.orgmemory.core.organization.CurrentActor;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Permission-aware action hints for the source catalogue. Mutations still recheck authority. */
@Service
class SecureSourceActionAuthorizationAdapter implements SourceActionAuthorizationPort {

    private static final PermissionKey CAN_DELETE = PermissionKey.of("can_delete");
    private static final String RESOURCE_TYPE = "knowledge_asset";

    private final RelationshipAuthorizationSetPort authorization;
    private final KnowledgeRetrievalProperties properties;

    SecureSourceActionAuthorizationAdapter(
            RelationshipAuthorizationSetPort authorization,
            KnowledgeRetrievalProperties properties) {
        this.authorization = authorization;
        this.properties = properties;
    }

    @Override
    public Set<UUID> deletableKnowledgeAssetIds(CurrentActor actor) {
        var listed = authorization.listAuthorizedResources(new AuthorizedResourceQuery(
                actor.organizationId(), actor.principal(), CAN_DELETE, RESOURCE_TYPE));
        if (!listed.resolved()) {
            return Set.of();
        }
        if (listed.resources().size() > properties.maximumAuthorizedObjects()) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Document action permissions are inconsistent");
        }
        LinkedHashSet<UUID> assetIds = new LinkedHashSet<>();
        try {
            for (var resource : listed.resources()) {
                if (!actor.organizationId().equals(resource.organizationId())
                        || !RESOURCE_TYPE.equals(resource.type())
                        || !assetIds.add(UUID.fromString(resource.id()))) {
                    throw new KnowledgeRetrievalUnavailableException(
                            "Document action permissions are inconsistent");
                }
            }
        } catch (IllegalArgumentException invalidIdentifier) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Document action permissions are inconsistent");
        }
        return Set.copyOf(assetIds);
    }
}
