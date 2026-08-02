package com.orgmemory.core.assetregistry.kernel;

import com.orgmemory.core.assetregistry.api.AssetAuthorizationTarget;
import com.orgmemory.core.assetregistry.api.AssetAuthorizationTargetQuery;
import com.orgmemory.core.assetregistry.api.AssetConflictException;
import com.orgmemory.core.assetregistry.api.AssetIdentity;
import com.orgmemory.core.assetregistry.api.AssetIdentityQuery;
import com.orgmemory.core.assetregistry.api.AssetNotFoundException;
import com.orgmemory.core.assetregistry.api.AssetPortfolioCommand;
import com.orgmemory.core.assetregistry.api.AssetRegistrationCommand;
import com.orgmemory.core.assetregistry.api.AssetRole;
import com.orgmemory.core.assetregistry.api.AssetRoleCommand;
import com.orgmemory.core.assetregistry.api.AssetRoleQuery;
import com.orgmemory.core.authorization.RelationshipTuple;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssetKernelService implements
        AssetRegistrationCommand,
        AssetRoleCommand,
        AssetPortfolioCommand,
        AssetIdentityQuery,
        AssetAuthorizationTargetQuery,
        AssetRoleQuery {

    private final AssetRepository assets;
    private final AssetRoleAssignmentRepository roles;
    private final AssetAuthorizationOutboxRepository outbox;

    AssetKernelService(
            AssetRepository assets,
            AssetRoleAssignmentRepository roles,
            AssetAuthorizationOutboxRepository outbox) {
        this.assets = assets;
        this.roles = roles;
        this.outbox = outbox;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID register(NewAsset command) {
        Asset asset = new Asset(
                command.organizationId(),
                command.type(),
                command.namespace(),
                command.slug(),
                command.knowledgeSpaceId());
        try {
            assets.saveAndFlush(asset);
        } catch (DataIntegrityViolationException duplicate) {
            throw new AssetConflictException(
                    "An Asset already uses this namespace and slug, or the Space is unavailable",
                    duplicate);
        }
        Instant now = Instant.now();
        AssetRoleAssignment owner = roles.saveAndFlush(new AssetRoleAssignment(
                command.organizationId(),
                asset.getId(),
                command.owner(),
                AssetRole.OWNER,
                command.assignedByUserId(),
                now));
        String object = "asset:" + asset.getId();
        outbox.saveAllAndFlush(List.of(
                new AssetAuthorizationOutbox(
                        command.organizationId(),
                        asset.getId(),
                        null,
                        RelationshipTuple.of(
                                "organization:" + command.organizationId(),
                                "organization",
                                object)),
                new AssetAuthorizationOutbox(
                        command.organizationId(),
                        asset.getId(),
                        null,
                        RelationshipTuple.of(
                                "knowledge_space:" + command.knowledgeSpaceId(),
                                "space",
                                object)),
                new AssetAuthorizationOutbox(
                        command.organizationId(),
                        asset.getId(),
                        owner.getId(),
                        RelationshipTuple.of(
                                command.owner().openFgaUser(),
                                AssetRole.OWNER.relation(),
                                object))));
        return asset.getId();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID assign(Assignment command) {
        Asset asset = requiredForUpdate(command.organizationId(), command.assetId());
        if (roles.findByAssetIdAndPrincipalTypeAndPrincipalIdAndRoleAndValidUntilIsNull(
                        asset.getId(),
                        command.principal().type(),
                        command.principal().id(),
                        command.role())
                .isPresent()) {
            throw new AssetConflictException("This active Asset role assignment already exists");
        }
        Instant now = Instant.now();
        AssetRoleAssignment assignment = roles.saveAndFlush(new AssetRoleAssignment(
                command.organizationId(),
                asset.getId(),
                command.principal(),
                command.role(),
                command.assignedByUserId(),
                now));
        outbox.saveAndFlush(new AssetAuthorizationOutbox(
                command.organizationId(),
                asset.getId(),
                assignment.getId(),
                RelationshipTuple.of(
                        command.principal().openFgaUser(),
                        command.role().relation(),
                        "asset:" + asset.getId())));
        return assignment.getId();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AssetIdentity activateAfterRelease(UUID organizationId, UUID assetId) {
        Asset asset = requiredForUpdate(organizationId, assetId);
        asset.activate();
        return identity(assets.save(asset));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AssetIdentity startSunsettingAfterReleaseChange(
            UUID organizationId, UUID assetId) {
        Asset asset = requiredForUpdate(organizationId, assetId);
        asset.startSunsetting();
        return identity(assets.save(asset));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AssetIdentity retireAfterFinalWithdrawal(UUID organizationId, UUID assetId) {
        Asset asset = requiredForUpdate(organizationId, assetId);
        asset.retire();
        return identity(assets.save(asset));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssetIdentity> findById(UUID organizationId, UUID assetId) {
        return assets.findByIdAndOrganizationId(assetId, organizationId)
                .map(AssetKernelService::identity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssetIdentity> findByCoordinate(
            UUID organizationId, String namespace, String slug) {
        return assets.findByOrganizationIdAndNamespaceAndSlug(organizationId, namespace, slug)
                .map(AssetKernelService::identity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssetAuthorizationTarget> find(UUID organizationId, UUID assetId) {
        return findById(organizationId, assetId)
                .map(asset -> new AssetAuthorizationTarget(
                        asset.organizationId(),
                        asset.id(),
                        asset.knowledgeSpaceId(),
                        asset.type(),
                        asset.authorizationReady()));
    }

    @Override
    @Transactional(readOnly = true)
    public RoleHistory history(UUID organizationId, UUID assetId, Instant viewedAt) {
        findById(organizationId, assetId).orElseThrow(AssetNotFoundException::new);
        List<AssetRoleAssignment> assignments = roles.findByAssetIdOrderByValidFromAsc(assetId);
        boolean ownerPresent = hasActiveRole(assignments, AssetRole.OWNER, viewedAt);
        boolean backupOwnerPresent = hasActiveRole(assignments, AssetRole.BACKUP_OWNER, viewedAt);
        return new RoleHistory(
                new OwnershipHealth(
                        ownerPresent,
                        backupOwnerPresent,
                        !ownerPresent && !backupOwnerPresent,
                        !ownerPresent || !backupOwnerPresent),
                assignments.stream().map(AssetKernelService::roleView).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> activeAssetIdsForUserRole(
            UUID organizationId, String userId, AssetRole role, Instant viewedAt) {
        return roles.findActiveAssetIdsForUserRole(
                organizationId, userId, role, viewedAt);
    }

    private Asset requiredForUpdate(UUID organizationId, UUID assetId) {
        return assets.findForUpdate(assetId, organizationId)
                .orElseThrow(AssetNotFoundException::new);
    }

    private static AssetIdentity identity(Asset asset) {
        return new AssetIdentity(
                asset.getOrganizationId(),
                asset.getId(),
                asset.getType(),
                asset.getNamespace(),
                asset.getSlug(),
                asset.getKnowledgeSpaceId(),
                asset.getPortfolioState(),
                asset.isAuthorizationReady());
    }

    private static boolean hasActiveRole(
            List<AssetRoleAssignment> assignments, AssetRole role, Instant viewedAt) {
        return assignments.stream().anyMatch(assignment ->
                assignment.getRole() == role
                        && (assignment.getValidUntil() == null
                                || assignment.getValidUntil().isAfter(viewedAt)));
    }

    private static RoleAssignment roleView(AssetRoleAssignment assignment) {
        return new RoleAssignment(
                assignment.getId(),
                assignment.getPrincipalType(),
                assignment.getPrincipalId(),
                assignment.getRole(),
                assignment.getValidFrom(),
                assignment.getValidUntil(),
                assignment.getAssignedByUserId(),
                assignment.getProjectedAt());
    }
}
