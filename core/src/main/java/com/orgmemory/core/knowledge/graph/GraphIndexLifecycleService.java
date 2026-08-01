package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphQuery;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphRef;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionGraphRef;

import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authorization boundary around status, cancellation and unfinished-work
 * recovery.
 *
 * <p>A succeeded job is final for its immutable graph-processing profile. A new
 * profile may enqueue a new projection for the same immutable source revision;
 * delete rebuilds the effective graph by removing only the retired revision's
 * contributions.
 */
@Service
public class GraphIndexLifecycleService {

    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final PermissionKey CAN_REBUILD = PermissionKey.of("can_rebuild");
    private static final String RESOURCE_TYPE = "knowledge_asset";

    private final GraphIndexingCoordinator coordinator;
    private final GraphIndexJobQueue queue;
    private final KnowledgeAssetGraphQuery assets;
    private final RelationshipAuthorizationPort authorization;

    GraphIndexLifecycleService(
            GraphIndexingCoordinator coordinator,
            GraphIndexJobQueue queue,
            KnowledgeAssetGraphQuery assets,
            RelationshipAuthorizationPort authorization) {
        this.coordinator = coordinator;
        this.queue = queue;
        this.assets = assets;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public GraphIndexJobView status(CurrentActor actor, UUID jobId) {
        Objects.requireNonNull(actor, "actor");
        GraphIndexJobView view =
                coordinator.status(actor.organizationId(), jobId);
        require(actor, CAN_VIEW, view.knowledgeAssetId());
        return view;
    }

    @Transactional
    public GraphIndexJobView cancel(CurrentActor actor, UUID jobId) {
        Objects.requireNonNull(actor, "actor");
        GraphIndexJobView view =
                coordinator.status(actor.organizationId(), jobId);
        require(actor, CAN_REBUILD, view.knowledgeAssetId());
        return coordinator.cancel(actor.organizationId(), jobId);
    }

    @Transactional
    public GraphIndexJobView resume(CurrentActor actor, UUID jobId) {
        Objects.requireNonNull(actor, "actor");
        GraphIndexJobView view =
                coordinator.status(actor.organizationId(), jobId);
        require(actor, CAN_REBUILD, view.knowledgeAssetId());
        return coordinator.resume(actor.organizationId(), jobId);
    }

    @Transactional
    public GraphIndexJobView ensureCurrentProfile(
            CurrentActor actor,
            UUID knowledgeAssetId) {
        Objects.requireNonNull(actor, "actor");
        UUID assetId = Objects.requireNonNull(
                knowledgeAssetId, "knowledgeAssetId");
        require(actor, CAN_REBUILD, assetId);
        KnowledgeAssetGraphRef asset = assets
                .findAsset(actor.organizationId(), assetId)
                .filter(candidate -> !candidate.archived())
                .orElseThrow(() -> new IllegalStateException(
                        "Graph indexing requires an active, non-archived Knowledge Asset"));
        KnowledgeAssetVersionGraphRef version = assets
                .findVersion(
                        actor.organizationId(),
                        Objects.requireNonNull(
                                asset.currentVersionId(), "currentVersionId"))
                .filter(KnowledgeAssetVersionGraphRef::active)
                .orElseThrow(() -> new IllegalStateException(
                        "Graph indexing requires an active Knowledge Asset version"));
        UUID jobId = queue.enqueue(
                actor.organizationId(),
                Objects.requireNonNull(
                        version.sourceRevisionId(), "sourceRevisionId"),
                assetId,
                version.id(),
                java.time.Instant.now());
        return coordinator.status(actor.organizationId(), jobId);
    }

    private void require(
            CurrentActor actor, PermissionKey permission, UUID knowledgeAssetId) {
        var decision = authorization.check(new RelationshipAuthorizationQuery(
                actor.principal(),
                permission,
                ResourceRef.of(
                        actor.organizationId(),
                        RESOURCE_TYPE,
                        knowledgeAssetId)));
        if (!decision.allowed()) {
            throw new OrgMemoryAccessDeniedException(
                    "The current user is not authorized to manage graph indexing");
        }
    }
}
