package com.orgmemory.core.assetregistry;

import com.orgmemory.core.authorization.AuthorizedResourceQuery;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.PrincipalRef;
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

@Service
public class AssetRegistryService {

    private static final String ASSET_RESOURCE = "asset";
    private static final String SPACE_RESOURCE = "knowledge_space";
    private static final PermissionKey CAN_CREATE_ASSET = PermissionKey.of("can_create_asset");
    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final PermissionKey CAN_EDIT = PermissionKey.of("can_edit");
    private static final PermissionKey CAN_SUBMIT_REVIEW =
            PermissionKey.of("can_submit_review");
    private static final PermissionKey CAN_REVIEW = PermissionKey.of("can_review");
    private static final PermissionKey CAN_PUBLISH = PermissionKey.of("can_publish");
    private static final PermissionKey CAN_WITHDRAW = PermissionKey.of("can_withdraw");
    private static final PermissionKey CAN_USE = PermissionKey.of("can_use");
    private static final PermissionKey CAN_MANAGE_ROLES =
            PermissionKey.of("can_manage_roles");

    private final AssetRegistryCoordinator coordinator;
    private final AssetAuthorizationProjectionService projection;
    private final RelationshipAuthorizationPort authorization;
    private final RelationshipAuthorizationSetPort authorizationSets;

    AssetRegistryService(
            AssetRegistryCoordinator coordinator,
            AssetAuthorizationProjectionService projection,
            RelationshipAuthorizationPort authorization,
            RelationshipAuthorizationSetPort authorizationSets) {
        this.coordinator = coordinator;
        this.projection = projection;
        this.authorization = authorization;
        this.authorizationSets = authorizationSets;
    }

    public AssetView create(
            CurrentActor actor,
            AssetType type,
            String namespace,
            String slug,
            UUID knowledgeSpaceId,
            AssetDraftInput input) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        requireParentCreate(actor, knowledgeSpaceId);
        UUID assetId = coordinator.create(
                actor, type, namespace, slug, knowledgeSpaceId, input);
        projection.project(actor.organizationId(), assetId);
        return coordinator.view(actor.organizationId(), assetId);
    }

    public List<AssetSummary> list(CurrentActor actor) {
        return search(actor, null, null);
    }

    public List<AssetSummary> search(
            CurrentActor actor, String query, AssetType type) {
        Objects.requireNonNull(actor, "actor");
        var result = authorizationSets.listAuthorizedResources(new AuthorizedResourceQuery(
                actor.organizationId(), actor.principal(), CAN_VIEW, ASSET_RESOURCE));
        if (!result.resolved()) {
            throw new AssetUnavailableException("Asset permissions are temporarily unavailable");
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (ResourceRef resource : result.resources()) {
            if (!actor.organizationId().equals(resource.organizationId())
                    || !ASSET_RESOURCE.equals(resource.type())) {
                throw new AssetUnavailableException(
                        "Asset permissions are inconsistent with the catalog");
            }
            try {
                ids.add(UUID.fromString(resource.id()));
            } catch (IllegalArgumentException invalidId) {
                throw new AssetUnavailableException(
                        "Asset permissions are inconsistent with the catalog",
                        invalidId);
            }
        }
        return coordinator.summaries(actor.organizationId(), ids, query, type);
    }

    public AssetView get(CurrentActor actor, UUID assetId) {
        require(actor, assetId, CAN_VIEW);
        return coordinator.view(actor.organizationId(), assetId);
    }

    public AssetConsumptionRelease releaseForUse(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            AssetType expectedType) {
        AssetConsumptionRelease release = releaseForUse(actor, assetId, releaseId);
        if (release.type() != Objects.requireNonNull(expectedType, "expectedType")) {
            throw new AssetNotFoundException();
        }
        return release;
    }

    public AssetConsumptionRelease releaseForUse(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId) {
        require(actor, assetId, CAN_USE);
        return coordinator.consumptionRelease(
                actor.organizationId(), assetId, releaseId);
    }

    public AssetView forkRelease(
            CurrentActor actor,
            UUID sourceAssetId,
            UUID sourceReleaseId,
            String namespace,
            String slug,
            UUID knowledgeSpaceId) {
        AssetConsumptionRelease source =
                releaseForUse(actor, sourceAssetId, sourceReleaseId);
        return create(
                actor,
                source.type(),
                namespace,
                slug,
                knowledgeSpaceId,
                new AssetDraftInput(
                        source.title(),
                        source.summary(),
                        source.classification(),
                        source.schemaVersion(),
                        source.payload()));
    }

    public AssetView updateDraft(
            CurrentActor actor,
            UUID assetId,
            long expectedLockVersion,
            AssetDraftInput input) {
        require(actor, assetId, CAN_EDIT);
        return coordinator.updateDraft(actor, assetId, expectedLockVersion, input);
    }

    public AssetView submit(CurrentActor actor, UUID assetId, String changeNote) {
        require(actor, assetId, CAN_SUBMIT_REVIEW);
        return coordinator.submit(actor, assetId, changeNote);
    }

    public AssetView decide(
            CurrentActor actor,
            UUID assetId,
            UUID reviewCaseId,
            AssetReviewDecisionType decision,
            String comment) {
        PermissionKey permission = decision == AssetReviewDecisionType.CANCEL
                ? CAN_SUBMIT_REVIEW
                : CAN_REVIEW;
        require(actor, assetId, permission);
        return coordinator.decide(
                actor, assetId, reviewCaseId, decision, comment);
    }

    public AssetView publish(
            CurrentActor actor,
            UUID assetId,
            UUID revisionId,
            String versionLabel) {
        require(actor, assetId, CAN_PUBLISH);
        return coordinator.publish(actor, assetId, revisionId, versionLabel);
    }

    public AssetView deprecate(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            String reason) {
        require(actor, assetId, CAN_WITHDRAW);
        return coordinator.changeAvailability(
                actor, assetId, releaseId, AssetAvailability.DEPRECATED, reason);
    }

    public AssetView withdraw(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            String reason) {
        require(actor, assetId, CAN_WITHDRAW);
        return coordinator.changeAvailability(
                actor, assetId, releaseId, AssetAvailability.WITHDRAWN, reason);
    }

    public AssetView assignRole(
            CurrentActor actor,
            UUID assetId,
            String principalType,
            String principalId,
            AssetRole role) {
        require(actor, assetId, CAN_MANAGE_ROLES);
        UUID projectedAssetId = coordinator.assignRole(
                actor,
                assetId,
                new PrincipalRef(principalType, principalId),
                role);
        projection.project(actor.organizationId(), projectedAssetId);
        return coordinator.view(actor.organizationId(), projectedAssetId);
    }

    private void requireParentCreate(CurrentActor actor, UUID knowledgeSpaceId) {
        var decision = authorization.check(new RelationshipAuthorizationQuery(
                actor.principal(),
                CAN_CREATE_ASSET,
                ResourceRef.of(actor.organizationId(), SPACE_RESOURCE, knowledgeSpaceId)));
        if (!decision.allowed()) {
            throw new OrgMemoryAccessDeniedException(
                    "The current user cannot create an Asset in this Space");
        }
    }

    private void require(CurrentActor actor, UUID assetId, PermissionKey permission) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(assetId, "assetId");
        AssetAuthorizationTarget target = coordinator
                .target(actor.organizationId(), assetId)
                .filter(AssetAuthorizationTarget::authorizationReady)
                .orElseThrow(AssetNotFoundException::new);
        var decision = authorization.check(new RelationshipAuthorizationQuery(
                actor.principal(),
                permission,
                ResourceRef.of(
                        target.organizationId(), ASSET_RESOURCE, target.assetId())));
        if (!decision.allowed()) {
            throw new AssetNotFoundException();
        }
    }
}
