package com.orgmemory.core.assetregistry;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssetAuthorizationCoordinator {

    private final AssetRepository assets;
    private final AssetRoleAssignmentRepository roles;
    private final AssetAuthorizationOutboxRepository outbox;

    AssetAuthorizationCoordinator(
            AssetRepository assets,
            AssetRoleAssignmentRepository roles,
            AssetAuthorizationOutboxRepository outbox) {
        this.assets = assets;
        this.roles = roles;
        this.outbox = outbox;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AssetAuthorizationBatch startAttempt(UUID organizationId, UUID assetId) {
        Asset asset = assets.findByIdAndOrganizationId(assetId, organizationId)
                .orElseThrow(AssetNotFoundException::new);
        List<AssetAuthorizationOutbox> pending =
                outbox.findForAsset(asset.getId(), AssetAuthorizationStatus.PENDING);
        pending.forEach(AssetAuthorizationOutbox::startAttempt);
        outbox.saveAllAndFlush(pending);
        return new AssetAuthorizationBatch(
                organizationId,
                assetId,
                pending.stream().map(AssetAuthorizationOutbox::getId).toList(),
                pending.stream().map(AssetAuthorizationOutbox::tuple).toList());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void complete(AssetAuthorizationBatch batch, String modelId) {
        Instant appliedAt = Instant.now();
        List<AssetAuthorizationOutbox> records = outbox.findByIdIn(batch.outboxIds());
        if (records.size() != batch.outboxIds().size()) {
            throw new IllegalStateException("Asset authorization outbox batch is incomplete");
        }
        Set<UUID> roleIds = records.stream()
                .map(AssetAuthorizationOutbox::getRoleAssignmentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        records.forEach(record -> record.markApplied(modelId, appliedAt));
        outbox.saveAll(records);
        if (!roleIds.isEmpty()) {
            List<AssetRoleAssignment> assignments = roles.findAllById(roleIds);
            assignments.forEach(role -> role.markProjected(appliedAt));
            roles.saveAll(assignments);
        }
        if (outbox.countPending(batch.assetId()) == 0) {
            Asset asset = assets.findByIdAndOrganizationId(batch.assetId(), batch.organizationId())
                    .orElseThrow(AssetNotFoundException::new);
            asset.markAuthorizationReady();
            assets.save(asset);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordFailure(AssetAuthorizationBatch batch, String code, String message) {
        List<AssetAuthorizationOutbox> records = outbox.findByIdIn(batch.outboxIds());
        records.forEach(record -> record.recordFailure(code, message));
        outbox.saveAll(records);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    List<AssetAuthorizationCandidate> pendingAssets(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Convergence limit must be positive");
        }
        return outbox.findPending(PageRequest.of(0, limit)).stream()
                .map(record -> new AssetAuthorizationCandidate(
                        record.getOrganizationId(), record.getAssetId()))
                .distinct()
                .toList();
    }
}
