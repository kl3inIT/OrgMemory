package com.orgmemory.core.assetregistry.api;

import com.orgmemory.core.authorization.PrincipalRef;
import java.util.Objects;
import java.util.UUID;

/** Atomic ownership and collaboration changes with authorization projection intent. */
public interface AssetSharingCommand {

    AssetIdentity share(Share command);

    AssetIdentity unshare(Unshare command);

    AssetIdentity transferOwnership(TransferOwnership command);

    AssetIdentity recoverOwnership(RecoverOwnership command);

    record Share(
            UUID organizationId,
            UUID assetId,
            PrincipalRef principal,
            AssetRole role,
            UUID actorUserId) {

        public Share {
            requireCommon(organizationId, assetId, principal, actorUserId);
            if (role != AssetRole.VIEWER && role != AssetRole.EDITOR) {
                throw new IllegalArgumentException("Asset sharing supports Viewer or Editor only");
            }
            if (principal.type().equals("organization") && role != AssetRole.VIEWER) {
                throw new IllegalArgumentException("Organization sharing supports Viewer only");
            }
        }
    }

    record Unshare(
            UUID organizationId,
            UUID assetId,
            PrincipalRef principal,
            AssetRole role,
            UUID actorUserId) {

        public Unshare {
            requireCommon(organizationId, assetId, principal, actorUserId);
            if (role != AssetRole.VIEWER && role != AssetRole.EDITOR) {
                throw new IllegalArgumentException("Only Viewer or Editor shares may be removed");
            }
        }
    }

    record TransferOwnership(
            UUID organizationId,
            UUID assetId,
            UUID currentOwnerUserId,
            UUID nextOwnerUserId,
            UUID actorUserId) {

        public TransferOwnership {
            Objects.requireNonNull(organizationId, "organizationId");
            Objects.requireNonNull(assetId, "assetId");
            Objects.requireNonNull(currentOwnerUserId, "currentOwnerUserId");
            Objects.requireNonNull(nextOwnerUserId, "nextOwnerUserId");
            Objects.requireNonNull(actorUserId, "actorUserId");
            if (currentOwnerUserId.equals(nextOwnerUserId)) {
                throw new IllegalArgumentException("The next owner must be a different user");
            }
        }
    }

    record RecoverOwnership(
            UUID organizationId,
            UUID assetId,
            UUID nextOwnerUserId,
            UUID actorUserId) {

        public RecoverOwnership {
            Objects.requireNonNull(organizationId, "organizationId");
            Objects.requireNonNull(assetId, "assetId");
            Objects.requireNonNull(nextOwnerUserId, "nextOwnerUserId");
            Objects.requireNonNull(actorUserId, "actorUserId");
        }
    }

    private static void requireCommon(
            UUID organizationId,
            UUID assetId,
            PrincipalRef principal,
            UUID actorUserId) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(actorUserId, "actorUserId");
        if (!principal.type().equals("user")
                && !principal.type().equals("group")
                && !principal.type().equals("organization")) {
            throw new IllegalArgumentException(
                    "Asset sharing supports users, groups, or the organization");
        }
    }
}
