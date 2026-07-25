package com.orgmemory.core.assetregistry;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssetAuthorizationCoordinator {

    private static final Duration CLAIM_LEASE = Duration.ofMinutes(2);

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
        Asset asset = assets.findForUpdate(assetId, organizationId)
                .orElseThrow(AssetNotFoundException::new);
        Instant now = Instant.now();
        List<AssetAuthorizationOutbox> claimable =
                outbox.findClaimableForAsset(asset.getId(), now);
        AssetAuthorizationBatch batch =
                claimBatch(organizationId, assetId, claimable, now);
        outbox.saveAllAndFlush(claimable);
        return batch;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    List<AssetAuthorizationBatch> claimPendingBatches(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Convergence limit must be positive");
        }
        Instant now = Instant.now();
        List<AssetAuthorizationOutbox> claimable =
                outbox.findClaimable(now, PageRequest.of(0, limit));
        Map<UUID, List<AssetAuthorizationOutbox>> byAsset = new LinkedHashMap<>();
        for (AssetAuthorizationOutbox record : claimable) {
            byAsset.computeIfAbsent(record.getAssetId(), ignored -> new java.util.ArrayList<>())
                    .add(record);
        }
        List<AssetAuthorizationBatch> batches = byAsset.values().stream()
                .map(records -> claimBatch(
                        records.getFirst().getOrganizationId(),
                        records.getFirst().getAssetId(),
                        records,
                        now))
                .toList();
        outbox.saveAllAndFlush(claimable);
        return batches;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void complete(AssetAuthorizationBatch batch, String modelId) {
        Asset asset = assets.findForUpdate(batch.assetId(), batch.organizationId())
                .orElseThrow(AssetNotFoundException::new);
        Instant appliedAt = Instant.now();
        List<AssetAuthorizationOutbox> records = outbox.findByIdIn(batch.outboxIds());
        if (records.size() != batch.outboxIds().size()) {
            throw new IllegalStateException("Asset authorization outbox batch is incomplete");
        }
        Set<UUID> roleIds = records.stream()
                .map(AssetAuthorizationOutbox::getRoleAssignmentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        records.forEach(
                record -> record.markApplied(batch.claimToken(), modelId, appliedAt));
        outbox.saveAll(records);
        if (!roleIds.isEmpty()) {
            List<AssetRoleAssignment> assignments = roles.findAllById(roleIds);
            assignments.forEach(role -> role.markProjected(appliedAt));
            roles.saveAll(assignments);
        }
        if (outbox.countUnresolved(batch.assetId()) == 0) {
            asset.markAuthorizationReady();
            assets.save(asset);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordFailure(AssetAuthorizationBatch batch, String code, String message) {
        List<AssetAuthorizationOutbox> records = outbox.findByIdIn(batch.outboxIds());
        if (records.size() != batch.outboxIds().size()) {
            throw new AssetConflictException(
                    "Asset authorization outbox batch is incomplete");
        }
        Instant failedAt = Instant.now();
        records.forEach(record -> record.recordFailure(
                batch.claimToken(), code, message, failedAt));
        outbox.saveAll(records);
    }

    private static AssetAuthorizationBatch claimBatch(
            UUID organizationId,
            UUID assetId,
            List<AssetAuthorizationOutbox> records,
            Instant now) {
        UUID claimToken = UUID.randomUUID();
        Instant leaseUntil = now.plus(CLAIM_LEASE);
        records.forEach(record -> record.claim(claimToken, now, leaseUntil));
        return new AssetAuthorizationBatch(
                organizationId,
                assetId,
                claimToken,
                records.stream().map(AssetAuthorizationOutbox::getId).toList(),
                records.stream().map(AssetAuthorizationOutbox::tuple).toList());
    }
}
