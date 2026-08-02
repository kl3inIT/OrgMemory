package com.orgmemory.core.assetregistry.api;

import com.orgmemory.core.authorization.PrincipalRef;
import java.util.Objects;
import java.util.UUID;

/** Atomic accountable-role assignment plus its authorization intent. */
public interface AssetRoleCommand {

    UUID assign(Assignment command);

    record Assignment(
            UUID organizationId,
            UUID assetId,
            PrincipalRef principal,
            AssetRole role,
            UUID assignedByUserId) {

        public Assignment {
            Objects.requireNonNull(organizationId, "organizationId");
            Objects.requireNonNull(assetId, "assetId");
            Objects.requireNonNull(principal, "principal");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(assignedByUserId, "assignedByUserId");
        }
    }
}
