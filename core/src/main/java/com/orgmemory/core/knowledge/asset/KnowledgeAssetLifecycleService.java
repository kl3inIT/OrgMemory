package com.orgmemory.core.knowledge.asset;

import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException;
import com.orgmemory.core.knowledge.sourceledger.SourceLifecycleService;
import com.orgmemory.core.knowledge.sourceledger.ReadyManualUploadRef;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permission-aware delete/rebuild boundary for a stable Knowledge Asset. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class KnowledgeAssetLifecycleService {

    private static final PermissionKey CAN_DELETE = PermissionKey.of("can_delete");
    private static final String RESOURCE_TYPE = "knowledge_asset";

    private final KnowledgeAssetRepository assets;
    private final KnowledgeAssetVersionRepository versions;
    private final RelationshipAuthorizationPort authorization;
    private final ModelInvocationCache modelCache;
    private final RetrievalResultCache retrievalCache;
    private final SourceLifecycleService sourceLifecycle;

    KnowledgeAssetLifecycleService(
            KnowledgeAssetRepository assets,
            KnowledgeAssetVersionRepository versions,
            RelationshipAuthorizationPort authorization,
            ModelInvocationCache modelCache,
            RetrievalResultCache retrievalCache,
            SourceLifecycleService sourceLifecycle) {
        this.assets = assets;
        this.versions = versions;
        this.authorization = authorization;
        this.modelCache = modelCache;
        this.retrievalCache = retrievalCache;
        this.sourceLifecycle = sourceLifecycle;
    }

    @Transactional
    public KnowledgeAssetRef deleteSource(CurrentActor actor, UUID sourceId) {
        Objects.requireNonNull(actor, "actor");
        ReadyManualUploadRef source = sourceLifecycle.requireReadyManualUpload(
                actor.organizationId(), Objects.requireNonNull(sourceId, "sourceId"));
        KnowledgeAsset asset = assets
                .findByIdAndOrganizationId(source.knowledgeAssetId(), actor.organizationId())
                .orElseThrow(KnowledgeAssetNotFoundException::new);
        if (!allowed(
                actor,
                CAN_DELETE,
                ResourceRef.of(actor.organizationId(), RESOURCE_TYPE, source.knowledgeAssetId()))) {
            throw new KnowledgeResourceNotFoundException();
        }
        KnowledgeAssetVersion version = versions
                .findByIdAndOrganizationId(
                        source.knowledgeAssetVersionId(), actor.organizationId())
                .filter(candidate -> candidate.getKnowledgeAssetId().equals(asset.getId()))
                .orElseThrow(() -> new IllegalStateException(
                        "Knowledge Asset version is missing"));

        if (!source.sourceArchived()) {
            if (!source.knowledgeAssetVersionId().equals(asset.getCurrentVersionId())) {
                throw new IllegalStateException("Source does not target the active Knowledge Asset version");
            }
            retire(actor, asset, version);
        } else if (asset.getArchivedAt() == null
                || version.getStatus() != KnowledgeAssetVersionStatus.RETIRED) {
            throw new IllegalStateException("Source and Knowledge Asset retirement state is inconsistent");
        }
        sourceLifecycle.archiveReadyManualUpload(
                actor.organizationId(), sourceId, source.knowledgeAssetId());
        return ref(asset, version);
    }

    @Transactional
    public KnowledgeAssetRef delete(CurrentActor actor, UUID knowledgeAssetId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        KnowledgeAsset asset = assets
                .findByIdAndOrganizationId(
                        knowledgeAssetId, actor.organizationId())
                .orElseThrow(KnowledgeAssetNotFoundException::new);
        require(
                actor,
                CAN_DELETE,
                ResourceRef.of(
                        actor.organizationId(),
                        RESOURCE_TYPE,
                        knowledgeAssetId));
        UUID currentVersionId = asset.getCurrentVersionId();
        if (currentVersionId == null) {
            throw new IllegalStateException(
                    "Knowledge Asset has no active version");
        }
        KnowledgeAssetVersion version = versions
                .findByIdAndOrganizationId(
                        currentVersionId, actor.organizationId())
                .filter(candidate -> candidate.getKnowledgeAssetId()
                        .equals(asset.getId()))
                .orElseThrow(() -> new IllegalStateException(
                        "Knowledge Asset version is missing"));
        retire(actor, asset, version);
        return ref(asset, version);
    }

    private void retire(
            CurrentActor actor,
            KnowledgeAsset asset,
            KnowledgeAssetVersion version) {
        Instant retiredAt = Instant.now();
        version.retire(retiredAt);
        asset.archive(retiredAt);
        versions.save(version);
        assets.save(asset);
        invalidate(KnowledgeProjectionNamespaces.forSpace(
                actor.organizationId(), asset.getKnowledgeSpaceId()));
    }

    private static KnowledgeAssetRef ref(
            KnowledgeAsset asset, KnowledgeAssetVersion version) {
        return new KnowledgeAssetRef(
                asset.getId(),
                version.getId(),
                version.getNormalizedRecordId(),
                version.getRawSourceObjectId(),
                version.getSourceAclSnapshotId(),
                version.getStatus());
    }

    private void invalidate(ProjectionNamespace namespace) {
        modelCache.invalidate(namespace);
        retrievalCache.invalidateNamespace(namespace);
    }

    private void require(
            CurrentActor actor,
            PermissionKey permission,
            ResourceRef resource) {
        if (!allowed(actor, permission, resource)) {
            throw new OrgMemoryAccessDeniedException(
                    "The current user is not authorized to delete this Knowledge Asset");
        }
    }

    private boolean allowed(
            CurrentActor actor,
            PermissionKey permission,
            ResourceRef resource) {
        return authorization
                .check(new RelationshipAuthorizationQuery(
                        actor.principal(), permission, resource))
                .allowed();
    }
}
