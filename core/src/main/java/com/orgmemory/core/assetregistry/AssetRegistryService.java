package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.skillstorage.SkillPackageStoragePort;
import com.orgmemory.core.assetregistry.consumption.AssetAvailability;
import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.assetregistry.consumption.AssetReleaseUseQuery;

import com.orgmemory.core.assetregistry.api.AssetAuthorizationTarget;
import com.orgmemory.core.assetregistry.api.AssetAuthorizationProjectionCommand;
import com.orgmemory.core.assetregistry.api.AssetConflictException;
import com.orgmemory.core.assetregistry.api.AssetNotFoundException;
import com.orgmemory.core.assetregistry.api.AssetRole;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.authorization.AuthorizedResourceQuery;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.PrincipalRef;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AssetRegistryService implements AssetReleaseUseQuery {

    private static final String ASSET_RESOURCE = "asset";
    private static final String SPACE_RESOURCE = "knowledge_space";
    private static final PermissionKey CAN_CREATE_ASSET = PermissionKey.of("can_create_asset");
    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final PermissionKey CAN_VIEW_RELEASED =
            PermissionKey.of("can_view_released");
    private static final PermissionKey CAN_VIEW_GOVERNANCE_HISTORY =
            PermissionKey.of("can_view_governance_history");
    private static final PermissionKey CAN_EDIT = PermissionKey.of("can_edit");
    private static final PermissionKey CAN_EDIT_DRAFT = PermissionKey.of("can_edit_draft");
    private static final PermissionKey CAN_SUBMIT_REVIEW =
            PermissionKey.of("can_submit_review");
    private static final PermissionKey CAN_REVIEW = PermissionKey.of("can_review");
    private static final PermissionKey CAN_PUBLISH = PermissionKey.of("can_publish");
    private static final PermissionKey CAN_PUBLISH_SKILL =
            PermissionKey.of("can_publish_skill");
    private static final PermissionKey CAN_WITHDRAW = PermissionKey.of("can_withdraw");
    private static final PermissionKey CAN_USE = PermissionKey.of("can_use");
    private static final PermissionKey CAN_MANAGE_ROLES =
            PermissionKey.of("can_manage_roles");
    private static final PermissionKey CAN_MANAGE_SHARING =
            PermissionKey.of("can_manage_sharing");
    private static final PermissionKey CAN_TRANSFER_OWNERSHIP =
            PermissionKey.of("can_transfer_ownership");
    private static final PermissionKey CAN_PUBLISH_DIRECT =
            PermissionKey.of("can_publish_direct");
    private static final PermissionKey CAN_RECOVER_OWNERSHIP =
            PermissionKey.of("can_recover_ownership");
    private static final PermissionKey CAN_EMERGENCY_WITHDRAW =
            PermissionKey.of("can_emergency_withdraw");

    private final AssetRegistryCoordinator coordinator;
    private final AssetAuthorizationProjectionCommand projection;
    private final RelationshipAuthorizationPort authorization;
    private final RelationshipAuthorizationSetPort authorizationSets;

    AssetRegistryService(
            AssetRegistryCoordinator coordinator,
            AssetAuthorizationProjectionCommand projection,
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
        if (type == AssetType.SKILL) {
            throw new BusinessValidationException(
                    "asset.creation-method-invalid",
                    "Skill Assets must be created from a validated package upload");
        }
        return createValidated(actor, type, namespace, slug, knowledgeSpaceId, input);
    }

    UUID createValidatedSkillIdentity(
            CurrentActor actor,
            String namespace,
            String slug,
            UUID knowledgeSpaceId,
            AssetDraftInput input,
            SkillPackageStoragePort.StoredSkillPackage storedPackage) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        requireParentCreate(actor, knowledgeSpaceId);
        return coordinator.createSkill(
                actor,
                namespace,
                slug,
                knowledgeSpaceId,
                input,
                storedPackage);
    }

    AssetView projectCreated(CurrentActor actor, UUID assetId) {
        projection.project(actor.organizationId(), assetId);
        return coordinator.view(actor.organizationId(), assetId);
    }

    void requireSkillCreate(CurrentActor actor, UUID knowledgeSpaceId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        requireParentCreate(actor, knowledgeSpaceId);
    }

    private AssetView createValidated(
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
        Set<UUID> ids = authorizedIds(actor, CAN_VIEW);
        return coordinator.summaries(actor.organizationId(), ids, query, type);
    }

    public List<AssetRecommendation> recommend(
            CurrentActor actor, String query, AssetType type) {
        Set<UUID> ids = authorizedIds(actor, CAN_USE);
        return coordinator.recommendations(
                actor.organizationId(), ids, query, type);
    }

    public AssetRecommendationPage catalog(
            CurrentActor actor,
            String query,
            AssetType type,
            AssetCatalogSort sort,
            int page,
            int pageSize) {
        int boundedPage = Math.max(page, 1);
        int boundedPageSize = Math.min(Math.max(pageSize, 1), 60);
        AssetCatalogSort selectedSort =
                sort == null ? AssetCatalogSort.RECENTLY_RELEASED : sort;
        Set<UUID> ids = authorizedIds(actor, CAN_USE);
        return coordinator.recommendationPage(
                actor.organizationId(),
                ids,
                query,
                type,
                selectedSort,
                boundedPage,
                boundedPageSize);
    }

    public AssetSummaryPage owned(
            CurrentActor actor,
            String query,
            AssetType type,
            AssetOwnedSort sort,
            int page,
            int pageSize) {
        int boundedPage = Math.max(page, 1);
        int boundedPageSize = Math.min(Math.max(pageSize, 1), 60);
        AssetOwnedSort selectedSort =
                sort == null ? AssetOwnedSort.RECENTLY_UPDATED : sort;
        Set<UUID> ids = authorizedIds(actor, CAN_VIEW);
        return coordinator.ownedSummaryPage(
                actor.organizationId(),
                actor.userId(),
                ids,
                query,
                type,
                selectedSort,
                boundedPage,
                boundedPageSize);
    }

    private Set<UUID> authorizedIds(
            CurrentActor actor, PermissionKey permission) {
        Objects.requireNonNull(actor, "actor");
        var result = authorizationSets.listAuthorizedResources(new AuthorizedResourceQuery(
                actor.organizationId(), actor.principal(), permission, ASSET_RESOURCE));
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
        return ids;
    }

    public AssetView get(CurrentActor actor, UUID assetId) {
        require(actor, assetId, CAN_VIEW_GOVERNANCE_HISTORY);
        return coordinator.view(actor.organizationId(), assetId);
    }

    public AssetReleasedView getReleased(CurrentActor actor, UUID assetId) {
        require(actor, assetId, CAN_VIEW_RELEASED);
        return coordinator.releasedView(actor.organizationId(), assetId);
    }

    public AssetGovernanceActions governanceActions(
            CurrentActor actor, UUID assetId) {
        AssetAuthorizationTarget target = require(actor, assetId, CAN_VIEW_RELEASED);
        ResourceRef resource =
                ResourceRef.of(actor.organizationId(), ASSET_RESOURCE, assetId);
        boolean canEdit = allowed(actor, resource, CAN_EDIT);
        boolean canSubmitReview = allowed(
                actor,
                resource,
                CAN_SUBMIT_REVIEW);
        boolean canReview = allowed(actor, resource, CAN_REVIEW);
        boolean canPublish = allowed(actor, resource, CAN_PUBLISH);
        boolean canPublishSkill = target.type() == AssetType.SKILL
                && allowed(actor, resource, CAN_PUBLISH_SKILL);
        boolean canWithdraw = allowed(actor, resource, CAN_WITHDRAW);
        boolean canManageSharing = allowed(actor, resource, CAN_MANAGE_SHARING);
        boolean canTransferOwnership = allowed(actor, resource, CAN_TRANSFER_OWNERSHIP);
        boolean canPublishDirect = allowed(actor, resource, CAN_PUBLISH_DIRECT);
        AssetReviewDecisionActions review =
                coordinator.reviewDecisionActions(actor, assetId);
        boolean canApprove = canReview && review.canApprove();
        boolean canRequestChanges =
                canReview && review.canRequestChanges();
        boolean canReject = canReview && review.canReject();
        boolean canCancel = canSubmitReview && review.canCancel();
        boolean canOpenGovernance = canEdit
                || canSubmitReview
                || canReview
                || canApprove
                || canRequestChanges
                || canReject
                || canCancel
                || canPublish
                || canPublishSkill
                || canWithdraw
                || canManageSharing
                || canTransferOwnership
                || canPublishDirect;
        return new AssetGovernanceActions(
                canEdit,
                canSubmitReview,
                canReview,
                canApprove,
                canRequestChanges,
                canReject,
                canCancel,
                canPublish,
                canPublishSkill,
                canWithdraw,
                canManageSharing,
                canTransferOwnership,
                canPublishDirect,
                canOpenGovernance);
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

    @Override
    public AssetConsumptionRelease releaseForUse(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId) {
        require(actor, assetId, CAN_USE);
        return coordinator.consumptionRelease(
                actor.organizationId(), assetId, releaseId);
    }

    @Override
    public AssetConsumptionRelease latestReleaseForUse(
            CurrentActor actor, UUID assetId) {
        require(actor, assetId, CAN_USE);
        return coordinator.latestConsumptionRelease(
                actor.organizationId(), assetId);
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
        if (source.type() == AssetType.SKILL) {
            throw new AssetConflictException("Skill releases cannot be forked yet.");
        }
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
        require(actor, assetId, CAN_EDIT_DRAFT);
        return coordinator.updateDraft(actor, assetId, expectedLockVersion, input);
    }

    void requireSkillEdit(CurrentActor actor, UUID assetId) {
        require(actor, assetId, CAN_EDIT_DRAFT);
    }

    SkillDraftReplacement replaceValidatedSkillDraft(
            CurrentActor actor,
            UUID assetId,
            long expectedLockVersion,
            AssetDraftInput input,
            SkillPackageStoragePort.StoredSkillPackage storedPackage) {
        require(actor, assetId, CAN_EDIT_DRAFT);
        return coordinator.replaceSkillDraft(
                actor, assetId, expectedLockVersion, input, storedPackage);
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

    public AssetView publishSkillDraft(
            CurrentActor actor,
            UUID assetId,
            String versionLabel) {
        return publishDraft(actor, assetId, versionLabel);
    }

    public AssetView publishDraft(CurrentActor actor, UUID assetId, String versionLabel) {
        require(actor, assetId, CAN_PUBLISH_DIRECT);
        return coordinator.publishDraft(actor, assetId, versionLabel);
    }

    public AssetView share(
            CurrentActor actor,
            UUID assetId,
            String principalType,
            String principalId,
            AssetRole role) {
        require(actor, assetId, CAN_MANAGE_SHARING);
        PrincipalRef principal = sharingPrincipal(principalType, principalId);
        coordinator.share(actor, assetId, principal, role);
        projection.project(actor.organizationId(), assetId);
        return coordinator.view(actor.organizationId(), assetId);
    }

    public AssetView unshare(
            CurrentActor actor,
            UUID assetId,
            String principalType,
            String principalId,
            AssetRole role) {
        require(actor, assetId, CAN_MANAGE_SHARING);
        PrincipalRef principal = sharingPrincipal(principalType, principalId);
        coordinator.unshare(actor, assetId, principal, role);
        projection.project(actor.organizationId(), assetId);
        return coordinator.view(actor.organizationId(), assetId);
    }

    public AssetView transferOwnership(
            CurrentActor actor, UUID assetId, UUID nextOwnerUserId) {
        require(actor, assetId, CAN_TRANSFER_OWNERSHIP);
        coordinator.transferOwnership(actor, assetId, nextOwnerUserId);
        projection.project(actor.organizationId(), assetId);
        return coordinator.view(actor.organizationId(), assetId);
    }

    public AssetView recoverOwnership(
            CurrentActor actor, UUID assetId, UUID nextOwnerUserId) {
        require(actor, assetId, CAN_RECOVER_OWNERSHIP);
        coordinator.recoverOwnership(actor, assetId, nextOwnerUserId);
        projection.project(actor.organizationId(), assetId);
        return coordinator.view(actor.organizationId(), assetId);
    }

    public AssetView withdrawAsset(CurrentActor actor, UUID assetId, String reason) {
        require(actor, assetId, CAN_WITHDRAW);
        return coordinator.withdrawAsset(actor, assetId, reason, false);
    }

    public AssetView emergencyWithdrawAsset(
            CurrentActor actor, UUID assetId, String reason) {
        require(actor, assetId, CAN_EMERGENCY_WITHDRAW);
        return coordinator.withdrawAsset(actor, assetId, reason, true);
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
        if (role == AssetRole.VIEWER || role == AssetRole.EDITOR) {
            return share(actor, assetId, principalType, principalId, role);
        }
        if (role != AssetRole.REVIEWER && role != AssetRole.PUBLISHER) {
            throw new BusinessValidationException(
                    "asset.role-assignment-disabled",
                    "Ownership uses transfer and Asset collaboration uses Viewer or Editor");
        }
        require(actor, assetId, CAN_MANAGE_ROLES);
        PrincipalRef principal = sharingPrincipal(principalType, principalId);
        UUID projectedAssetId = coordinator.assignRole(actor, assetId, principal, role);
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

    private AssetAuthorizationTarget require(
            CurrentActor actor, UUID assetId, PermissionKey permission) {
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
        return target;
    }

    private boolean allowed(
            CurrentActor actor, ResourceRef resource, PermissionKey permission) {
        return authorization.check(new RelationshipAuthorizationQuery(
                        actor.principal(), permission, resource))
                .allowed();
    }

    private static PrincipalRef sharingPrincipal(String principalType, String principalId) {
        if (principalType == null || principalId == null) {
            throw new BusinessValidationException(
                    "asset.share-invalid", "The Asset share is invalid");
        }
        try {
            return new PrincipalRef(principalType, principalId);
        } catch (IllegalArgumentException invalidPrincipal) {
            throw new BusinessValidationException(
                    "asset.share-invalid", "The Asset share is invalid", invalidPrincipal);
        }
    }
}
