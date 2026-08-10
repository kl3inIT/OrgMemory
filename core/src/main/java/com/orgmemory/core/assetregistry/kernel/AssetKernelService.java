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
import com.orgmemory.core.assetregistry.api.AssetSharingCommand;
import com.orgmemory.core.assetregistry.api.AssetSharingState;
import com.orgmemory.core.authorization.PrincipalRef;
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
        AssetRoleQuery,
        AssetSharingCommand {

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
        if (!command.owner().type().equals("user")) {
            throw new IllegalArgumentException("An Asset owner must be a user");
        }
        UUID ownerUserId = UUID.fromString(command.owner().id());
        Asset asset = new Asset(
                command.organizationId(),
                command.type(),
                command.namespace(),
                command.slug(),
                command.knowledgeSpaceId(),
                ownerUserId);
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
                        AssetAuthorizationOperation.WRITE,
                        asset.getRelationshipGeneration(),
                        RelationshipTuple.of(
                                "organization:" + command.organizationId(),
                                "organization",
                                object)),
                new AssetAuthorizationOutbox(
                        command.organizationId(),
                        asset.getId(),
                        null,
                        AssetAuthorizationOperation.WRITE,
                        asset.getRelationshipGeneration(),
                        RelationshipTuple.of(
                                "knowledge_space:" + command.knowledgeSpaceId(),
                                "space",
                                object)),
                new AssetAuthorizationOutbox(
                        command.organizationId(),
                        asset.getId(),
                        owner.getId(),
                        AssetAuthorizationOperation.WRITE,
                        asset.getRelationshipGeneration(),
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
        long generation = asset.beginRelationshipChange();
        assets.save(asset);
        outbox.saveAndFlush(new AssetAuthorizationOutbox(
                command.organizationId(),
                asset.getId(),
                assignment.getId(),
                AssetAuthorizationOperation.WRITE,
                generation,
                RelationshipTuple.of(
                        openFgaSubject(command.principal()),
                        command.role().relation(),
                        "asset:" + asset.getId())));
        return assignment.getId();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AssetIdentity share(Share command) {
        Asset asset = requiredForUpdate(command.organizationId(), command.assetId());
        requireNoActiveAssignment(asset, command.principal(), command.role());
        Instant now = Instant.now();
        AssetRoleAssignment assignment = roles.saveAndFlush(new AssetRoleAssignment(
                command.organizationId(),
                asset.getId(),
                command.principal(),
                command.role(),
                command.actorUserId(),
                now));
        asset.updateSharingState(command.principal().type().equals("organization")
                ? AssetSharingState.ORGANIZATION
                : asset.getSharingState() == AssetSharingState.PRIVATE
                        ? AssetSharingState.SHARED
                        : asset.getSharingState());
        long generation = asset.beginRelationshipChange();
        assets.save(asset);
        queueRoleIntent(
                asset,
                assignment,
                command.principal(),
                command.role(),
                AssetAuthorizationOperation.WRITE,
                generation);
        return identity(asset);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AssetIdentity unshare(Unshare command) {
        Asset asset = requiredForUpdate(command.organizationId(), command.assetId());
        AssetRoleAssignment assignment = roles
                .findByAssetIdAndPrincipalTypeAndPrincipalIdAndRoleAndValidUntilIsNull(
                        asset.getId(),
                        command.principal().type(),
                        command.principal().id(),
                        command.role())
                .orElseThrow(() -> new AssetConflictException(
                        "This Asset share is already absent"));
        assignment.expire(expiryAfter(assignment, Instant.now()));
        roles.saveAndFlush(assignment);
        long generation = asset.beginRelationshipChange();
        asset.updateSharingState(deriveSharingState(asset.getId()));
        assets.save(asset);
        queueRoleIntent(
                asset,
                assignment,
                command.principal(),
                command.role(),
                AssetAuthorizationOperation.DELETE,
                generation);
        return identity(asset);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AssetIdentity transferOwnership(TransferOwnership command) {
        Asset asset = requiredForUpdate(command.organizationId(), command.assetId());
        if (!command.currentOwnerUserId().equals(asset.getOwnerUserId())) {
            throw new AssetConflictException("Asset ownership changed before this transfer");
        }
        AssetRoleAssignment currentOwner = activeUserRole(
                asset.getId(), command.currentOwnerUserId(), AssetRole.OWNER);
        Instant now = expiryAfter(currentOwner, Instant.now());
        currentOwner.expire(now);
        roles.saveAndFlush(currentOwner);

        PrincipalRef nextOwner = PrincipalRef.user(command.nextOwnerUserId());
        requireNoActiveAssignment(asset, nextOwner, AssetRole.OWNER);
        AssetRoleAssignment nextOwnerAssignment = roles.saveAndFlush(new AssetRoleAssignment(
                command.organizationId(),
                asset.getId(),
                nextOwner,
                AssetRole.OWNER,
                command.actorUserId(),
                now));

        PrincipalRef previousOwner = PrincipalRef.user(command.currentOwnerUserId());
        AssetRoleAssignment previousOwnerEditor = roles
                .findByAssetIdAndPrincipalTypeAndPrincipalIdAndRoleAndValidUntilIsNull(
                        asset.getId(), "user", previousOwner.id(), AssetRole.EDITOR)
                .orElseGet(() -> roles.saveAndFlush(new AssetRoleAssignment(
                        command.organizationId(),
                        asset.getId(),
                        previousOwner,
                        AssetRole.EDITOR,
                        command.actorUserId(),
                        now)));

        asset.transferOwnership(command.nextOwnerUserId());
        asset.updateSharingState(asset.getSharingState() == AssetSharingState.PRIVATE
                ? AssetSharingState.SHARED
                : asset.getSharingState());
        long generation = asset.beginRelationshipChange();
        assets.save(asset);
        queueRoleIntent(asset, currentOwner, previousOwner, AssetRole.OWNER,
                AssetAuthorizationOperation.DELETE, generation);
        queueRoleIntent(asset, nextOwnerAssignment, nextOwner, AssetRole.OWNER,
                AssetAuthorizationOperation.WRITE, generation);
        queueRoleIntent(asset, previousOwnerEditor, previousOwner, AssetRole.EDITOR,
                AssetAuthorizationOperation.WRITE, generation);
        return identity(asset);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AssetIdentity recoverOwnership(RecoverOwnership command) {
        Asset asset = requiredForUpdate(command.organizationId(), command.assetId());
        asset.recoverOwnership(command.nextOwnerUserId());
        PrincipalRef nextOwner = PrincipalRef.user(command.nextOwnerUserId());
        AssetRoleAssignment assignment = roles.saveAndFlush(new AssetRoleAssignment(
                command.organizationId(),
                asset.getId(),
                nextOwner,
                AssetRole.OWNER,
                command.actorUserId(),
                Instant.now()));
        long generation = asset.beginRelationshipChange();
        assets.save(asset);
        queueRoleIntent(asset, assignment, nextOwner, AssetRole.OWNER,
                AssetAuthorizationOperation.WRITE, generation);
        return identity(asset);
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
                asset.isAuthorizationReady(),
                asset.getOwnerUserId(),
                asset.getSharingState(),
                asset.getRelationshipGeneration(),
                asset.getProjectedRelationshipGeneration());
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

    private void requireNoActiveAssignment(
            Asset asset, PrincipalRef principal, AssetRole role) {
        if (roles.findByAssetIdAndPrincipalTypeAndPrincipalIdAndRoleAndValidUntilIsNull(
                        asset.getId(), principal.type(), principal.id(), role)
                .isPresent()) {
            throw new AssetConflictException("This active Asset share already exists");
        }
    }

    private AssetRoleAssignment activeUserRole(UUID assetId, UUID userId, AssetRole role) {
        return roles.findByAssetIdAndPrincipalTypeAndPrincipalIdAndRoleAndValidUntilIsNull(
                        assetId, "user", userId.toString(), role)
                .orElseThrow(() -> new AssetConflictException(
                        "The canonical Asset owner assignment is unavailable"));
    }

    private AssetSharingState deriveSharingState(UUID assetId) {
        List<AssetRoleAssignment> active = roles.findByAssetIdAndValidUntilIsNull(assetId);
        if (active.stream().anyMatch(assignment ->
                assignment.getPrincipalType().equals("organization")
                        && assignment.getRole() == AssetRole.VIEWER)) {
            return AssetSharingState.ORGANIZATION;
        }
        if (active.stream().anyMatch(assignment ->
                assignment.getRole() == AssetRole.VIEWER
                        || assignment.getRole() == AssetRole.EDITOR)) {
            return AssetSharingState.SHARED;
        }
        return AssetSharingState.PRIVATE;
    }

    private void queueRoleIntent(
            Asset asset,
            AssetRoleAssignment assignment,
            PrincipalRef principal,
            AssetRole role,
            AssetAuthorizationOperation operation,
            long generation) {
        outbox.saveAndFlush(new AssetAuthorizationOutbox(
                asset.getOrganizationId(),
                asset.getId(),
                assignment.getId(),
                operation,
                generation,
                RelationshipTuple.of(
                        openFgaSubject(principal),
                        role.relation(),
                        "asset:" + asset.getId())));
    }

    private static String openFgaSubject(PrincipalRef principal) {
        String subject = principal.openFgaUser();
        return principal.type().equals("group") || principal.type().equals("organization")
                ? subject + "#member"
                : subject;
    }

    private static Instant expiryAfter(AssetRoleAssignment assignment, Instant candidate) {
        return candidate.isAfter(assignment.getValidFrom())
                ? candidate
                : assignment.getValidFrom().plusNanos(1_000);
    }
}
