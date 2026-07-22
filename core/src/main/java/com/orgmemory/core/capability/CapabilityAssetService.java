package com.orgmemory.core.capability;

import com.orgmemory.core.authorization.AuthorizedResourceQuery;
import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.ContextualRelationship;
import com.orgmemory.core.authorization.EffectiveAuthorizationService;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.DepartmentRepository;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CapabilityAssetService {

    private static final PermissionKey CAN_CREATE = PermissionKey.of("can_create_capability_asset");
    private static final PermissionKey CAN_VIEW_REGISTRY = PermissionKey.of("can_view_capability_registry");
    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final PermissionKey CAN_EDIT = PermissionKey.of("can_edit");
    private static final PermissionKey CAN_REVIEW = PermissionKey.of("can_review");
    private static final String CAPABILITY_ASSET_TYPE = "capability_asset";

    private final CapabilityAssetRepository assets;
    private final AssetVersionRepository versions;
    private final AssetUsageEventRepository usageEvents;
    private final AssetApprovalEventRepository approvalEvents;
    private final AppUserRepository users;
    private final DepartmentRepository departments;
    private final EffectiveAuthorizationService authorization;
    private final RelationshipAuthorizationSetPort authorizationSets;

    public CapabilityAssetService(CapabilityAssetRepository assets, AssetVersionRepository versions,
            AssetUsageEventRepository usageEvents, AssetApprovalEventRepository approvalEvents,
            AppUserRepository users, DepartmentRepository departments,
            EffectiveAuthorizationService authorization,
            RelationshipAuthorizationSetPort authorizationSets) {
        this.assets = assets;
        this.versions = versions;
        this.usageEvents = usageEvents;
        this.approvalEvents = approvalEvents;
        this.users = users;
        this.departments = departments;
        this.authorization = authorization;
        this.authorizationSets = authorizationSets;
    }

    @Transactional
    public CapabilityAsset create(CurrentActor actor, CreateCapabilityAssetCommand command) {
        requireOrganizationPermission(actor, CAN_CREATE);
        CreateCapabilityAssetCommand effectiveCommand = actorScopedCommand(actor, command);
        validateReferences(effectiveCommand);

        CapabilityAsset asset = assets.save(new CapabilityAsset(effectiveCommand));
        AssetVersion version = versions.save(new AssetVersion(asset.getId(), 1, effectiveCommand));
        asset.setCurrentVersionId(version.getId());
        return assets.save(asset);
    }

    @Transactional(readOnly = true)
    public CapabilityAsset get(CurrentActor actor, UUID id) {
        requireOrganizationPermission(actor, CAN_VIEW_REGISTRY);
        CapabilityAsset asset = findInOrganization(actor, id);
        if (!canView(actor, asset)) {
            throw new CapabilityAssetNotFoundException(id);
        }
        return asset;
    }

    @Transactional(readOnly = true)
    public List<CapabilityAsset> search(CurrentActor actor, AssetStatus status, AssetType assetType, String query) {
        requireOrganizationPermission(actor, CAN_VIEW_REGISTRY);
        List<CapabilityAsset> candidates = status == null
                ? assets.findByOrganizationIdOrderByUpdatedAtDesc(actor.organizationId())
                : assets.findByOrganizationIdAndStatusOrderByUpdatedAtDesc(actor.organizationId(), status);
        List<CapabilityAsset> visibleCandidates = visibleAssets(actor, candidates);
        if (query == null || query.isBlank()) {
            return filterByAssetType(visibleCandidates, assetType);
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        return filterByAssetType(visibleCandidates, assetType).stream()
                .filter(asset -> contains(asset.getTitle(), normalized)
                        || contains(asset.getSummary(), normalized)
                        || asset.getAssetType().name().toLowerCase(Locale.ROOT).contains(normalized)
                        || contains(asset.getUseCase(), normalized)
                        || contains(asset.getBusinessProcess(), normalized)
                        || contains(asset.getAiTool(), normalized)
                        || contains(asset.getTagNames(), normalized))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CapabilityAssetListing> searchListings(
            CurrentActor actor, AssetStatus status, AssetType assetType, String query) {
        List<CapabilityAsset> visibleAssets = search(actor, status, assetType, query);
        if (visibleAssets.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> usageCounts = new HashMap<>();
        usageEvents.summarizeByAssetIds(visibleAssets.stream().map(CapabilityAsset::getId).toList())
                .forEach(total -> usageCounts.put(total.getAssetId(), total.getUsageCount()));
        return visibleAssets.stream()
                .map(asset -> new CapabilityAssetListing(asset, usageCounts.getOrDefault(asset.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssetVersion> versions(CurrentActor actor, UUID assetId) {
        get(actor, assetId);
        return versions.findByAssetIdOrderByVersionNumberDesc(assetId);
    }

    @Transactional(readOnly = true)
    public long usageCount(CurrentActor actor, UUID assetId) {
        get(actor, assetId);
        return usageEvents.countByAssetId(assetId);
    }

    @Transactional
    public CapabilityAsset submitForReview(CurrentActor actor, UUID assetId, String comment) {
        CapabilityAsset asset = findInOrganization(actor, assetId);
        requireAssetPermission(actor, asset, CAN_EDIT);
        asset.submitForReview();
        approvalEvents.save(new AssetApprovalEvent(assetId, actor.userId(), ApprovalAction.SUBMITTED, comment));
        return asset;
    }

    @Transactional
    public CapabilityAsset approve(CurrentActor actor, UUID assetId, String comment) {
        CapabilityAsset asset = findInOrganization(actor, assetId);
        requireAssetPermission(actor, asset, CAN_REVIEW);
        asset.approve();
        approvalEvents.save(new AssetApprovalEvent(assetId, actor.userId(), ApprovalAction.APPROVED, comment));
        return asset;
    }

    @Transactional
    public CapabilityAsset reject(CurrentActor actor, UUID assetId, String comment) {
        CapabilityAsset asset = findInOrganization(actor, assetId);
        requireAssetPermission(actor, asset, CAN_REVIEW);
        asset.reject();
        approvalEvents.save(new AssetApprovalEvent(assetId, actor.userId(), ApprovalAction.REJECTED, comment));
        return asset;
    }

    @Transactional
    public CapabilityAsset deprecate(CurrentActor actor, UUID assetId, String comment) {
        CapabilityAsset asset = findInOrganization(actor, assetId);
        requireAssetPermission(actor, asset, CAN_REVIEW);
        asset.deprecate();
        approvalEvents.save(new AssetApprovalEvent(assetId, actor.userId(), ApprovalAction.DEPRECATED, comment));
        return asset;
    }

    @Transactional
    public CapabilityAsset assignBackupOwner(CurrentActor actor, UUID assetId, UUID backupOwnerUserId, String comment) {
        CapabilityAsset asset = findInOrganization(actor, assetId);
        requireAssetPermission(actor, asset, CAN_EDIT);
        requireUserInOrganization(backupOwnerUserId, actor.organizationId());
        asset.assignBackupOwner(backupOwnerUserId);
        approvalEvents.save(new AssetApprovalEvent(assetId, actor.userId(), ApprovalAction.SUBMITTED, comment));
        return asset;
    }

    @Transactional
    public long recordUsage(CurrentActor actor, UUID assetId, UsageEventType eventType, String metadataJson) {
        get(actor, assetId);
        usageEvents.save(new AssetUsageEvent(assetId, actor.userId(), eventType, metadataJson));
        return usageEvents.countByAssetId(assetId);
    }

    private CreateCapabilityAssetCommand actorScopedCommand(CurrentActor actor, CreateCapabilityAssetCommand command) {
        UUID departmentId = command.departmentId() == null ? actor.departmentId() : command.departmentId();
        UUID ownerUserId = command.ownerUserId() == null ? actor.userId() : command.ownerUserId();
        return new CreateCapabilityAssetCommand(
                actor.organizationId(),
                departmentId,
                command.title(),
                command.summary(),
                command.assetType(),
                command.useCase(),
                command.businessProcess(),
                command.aiTool(),
                command.tagNames(),
                ownerUserId,
                command.backupOwnerUserId(),
                actor.userId(),
                command.visibility(),
                command.riskLevel(),
                command.promptTemplate(),
                command.workflowStepsJson(),
                command.inputSchemaJson(),
                command.outputSchemaJson(),
                command.exampleInput(),
                command.exampleOutput());
    }

    private void validateReferences(CreateCapabilityAssetCommand command) {
        if (command.departmentId() != null
                && !departments.existsByIdAndOrganizationId(command.departmentId(), command.organizationId())) {
            throw new OrgMemoryAccessDeniedException("Department does not belong to the current organization");
        }
        requireUserInOrganization(command.ownerUserId(), command.organizationId());
        if (command.backupOwnerUserId() != null) {
            requireUserInOrganization(command.backupOwnerUserId(), command.organizationId());
        }
    }

    private void requireUserInOrganization(UUID userId, UUID organizationId) {
        if (userId == null || !users.existsByIdAndOrganizationId(userId, organizationId)) {
            throw new OrgMemoryAccessDeniedException("User does not belong to the current organization");
        }
    }

    private CapabilityAsset findInOrganization(CurrentActor actor, UUID assetId) {
        return assets.findByIdAndOrganizationId(assetId, actor.organizationId())
                .orElseThrow(() -> new CapabilityAssetNotFoundException(assetId));
    }

    public void requireCreatePermission(CurrentActor actor) {
        requireOrganizationPermission(actor, CAN_CREATE);
    }

    public void requireRegistryPermission(CurrentActor actor) {
        requireOrganizationPermission(actor, CAN_VIEW_REGISTRY);
    }

    private void requireOrganizationPermission(CurrentActor actor, PermissionKey permission) {
        if (!authorization.authorize(
                actor.organizationId(),
                actor.principal(),
                permission,
                ResourceRef.of(actor.organizationId(), "organization", actor.organizationId())).allowed()) {
            throw new OrgMemoryAccessDeniedException("The current user is not authorized for this operation");
        }
    }

    private void requireAssetPermission(CurrentActor actor, CapabilityAsset asset, PermissionKey permission) {
        if (!isAllowed(actor, asset, permission)) {
            throw new OrgMemoryAccessDeniedException("The current user is not authorized for this asset operation");
        }
    }

    private boolean canView(CurrentActor actor, CapabilityAsset asset) {
        return isAllowed(actor, asset, CAN_VIEW);
    }

    private List<CapabilityAsset> visibleAssets(CurrentActor actor, List<CapabilityAsset> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        Set<ResourceRef> visibleResources = new HashSet<>();
        var listed = authorizationSets.listAuthorizedResources(new AuthorizedResourceQuery(
                actor.organizationId(), actor.principal(), CAN_VIEW, CAPABILITY_ASSET_TYPE));
        if (listed.resolved()) {
            visibleResources.addAll(listed.resources());
        }

        Map<ResourceRef, CapabilityAsset> candidatesByResource = new LinkedHashMap<>();
        Map<ResourceRef, List<ContextualRelationship>> contextualByResource = new LinkedHashMap<>();
        for (CapabilityAsset asset : candidates) {
            ResourceRef resource = ResourceRef.of(
                    actor.organizationId(), CAPABILITY_ASSET_TYPE, asset.getId());
            candidatesByResource.put(resource, asset);
            if (!visibleResources.contains(resource)) {
                contextualByResource.put(resource, contextualRelationships(asset));
            }
        }

        if (!contextualByResource.isEmpty()) {
            var checked = authorizationSets.batchCheck(new BatchAuthorizationQuery(
                    actor.organizationId(),
                    actor.principal(),
                    CAN_VIEW,
                    List.copyOf(contextualByResource.keySet()),
                    contextualByResource));
            if (checked.resolved()) {
                checked.decisions().forEach((resource, decision) -> {
                    if (decision.allowed()) {
                        visibleResources.add(resource);
                    }
                });
            }
        }

        return candidatesByResource.entrySet().stream()
                .filter(entry -> visibleResources.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    private boolean isAllowed(CurrentActor actor, CapabilityAsset asset, PermissionKey permission) {
        return authorization.authorize(
                actor.organizationId(),
                actor.principal(),
                permission,
                ResourceRef.of(actor.organizationId(), CAPABILITY_ASSET_TYPE, asset.getId()),
                contextualRelationships(asset)).allowed();
    }

    private static List<ContextualRelationship> contextualRelationships(CapabilityAsset asset) {
        String object = "capability_asset:" + asset.getId();
        List<ContextualRelationship> relationships = new java.util.ArrayList<>();
        relationships.add(ContextualRelationship.of(
                "organization:" + asset.getOrganizationId(), "organization", object));
        if (asset.getOwnerUserId() != null) {
            relationships.add(ContextualRelationship.of(
                    "user:" + asset.getOwnerUserId(), "owner", object));
        }
        if (asset.getCreatedByUserId() != null
                && !Objects.equals(asset.getCreatedByUserId(), asset.getOwnerUserId())) {
            relationships.add(ContextualRelationship.of(
                    "user:" + asset.getCreatedByUserId(), "owner", object));
        }
        if (asset.getStatus() == AssetStatus.APPROVED) {
            if (asset.getVisibility() == AssetVisibility.ORGANIZATION) {
                relationships.add(ContextualRelationship.of(
                        "organization:" + asset.getOrganizationId() + "#member", "viewer", object));
            } else if (asset.getVisibility() == AssetVisibility.TEAM && asset.getDepartmentId() != null) {
                relationships.add(ContextualRelationship.of(
                        "organizational_unit:" + asset.getDepartmentId() + "#member", "viewer", object));
            }
        }
        return List.copyOf(relationships);
    }

    private static boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private static List<CapabilityAsset> filterByAssetType(List<CapabilityAsset> assets, AssetType assetType) {
        if (assetType == null) {
            return assets;
        }
        return assets.stream()
                .filter(asset -> asset.getAssetType() == assetType)
                .toList();
    }
}
