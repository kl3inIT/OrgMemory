package com.orgmemory.core.assetregistry.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable accountable-role history and ownership health. */
public interface AssetRoleQuery {

    RoleHistory history(UUID organizationId, UUID assetId, Instant viewedAt);

    List<UUID> activeAssetIdsForUserRole(
            UUID organizationId, String userId, AssetRole role, Instant viewedAt);

    record RoleHistory(OwnershipHealth ownershipHealth, List<RoleAssignment> assignments) {

        public RoleHistory {
            assignments = List.copyOf(assignments);
        }
    }

    record RoleAssignment(
            UUID id,
            String principalType,
            String principalId,
            AssetRole role,
            Instant validFrom,
            Instant validUntil,
            UUID assignedByUserId,
            Instant projectedAt) {
    }

    record OwnershipHealth(
            boolean ownerPresent,
            boolean backupOwnerPresent,
            boolean orphaned,
            boolean continuityAtRisk) {
    }
}
