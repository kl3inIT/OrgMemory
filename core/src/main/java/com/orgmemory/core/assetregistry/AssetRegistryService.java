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
import com.orgmemory.core.shared.error.BusinessValidationException;
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
    private static final PermissionKey CAN_PUBLISH_SKILL =
            PermissionKey.of("can_publish_skill");
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
        require(actor, assetId, CAN_VIEW);
        return coordinator.view(actor.organizationId(), assetId);
    }

    public AssetGovernanceActions governanceActions(
            CurrentActor actor, UUID assetId) {
        AssetAuthorizationTarget target = require(actor, assetId, CAN_VIEW);
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
                || canWithdraw;
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

    public AssetConsumptionRelease releaseForUse(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId) {
        require(actor, assetId, CAN_USE);
        return coordinator.consumptionRelease(
                actor.organizationId(), assetId, releaseId);
    }

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
        require(actor, assetId, CAN_EDIT);
        return coordinator.updateDraft(actor, assetId, expectedLockVersion, input);
    }

    void requireSkillEdit(CurrentActor actor, UUID assetId) {
        require(actor, assetId, CAN_EDIT);
    }

    SkillDraftReplacement replaceValidatedSkillDraft(
            CurrentActor actor,
            UUID assetId,
            long expectedLockVersion,
            AssetDraftInput input,
            SkillPackageStoragePort.StoredSkillPackage storedPackage) {
        require(actor, assetId, CAN_EDIT);
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
        require(actor, assetId, CAN_PUBLISH_SKILL);
        return coordinator.publishSkillDraft(actor, assetId, versionLabel);
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
        if (role == null || principalType == null || principalId == null) {
            throw new BusinessValidationException(
                    "asset.role-assignment-invalid",
                    "The Asset role assignment is invalid");
        }
        PrincipalRef principal;
        try {
            principal = new PrincipalRef(principalType, principalId);
        } catch (IllegalArgumentException invalidRole) {
            throw new BusinessValidationException(
                    "asset.role-assignment-invalid",
                    "The Asset role assignment is invalid",
                    invalidRole);
        }
        UUID projectedAssetId = coordinator.assignRole(
                actor,
                assetId,
                principal,
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
}
