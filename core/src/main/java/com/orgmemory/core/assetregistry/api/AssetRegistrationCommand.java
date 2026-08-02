package com.orgmemory.core.assetregistry.api;

import com.orgmemory.core.authorization.PrincipalRef;
import java.util.Objects;
import java.util.UUID;

/** Atomic registration of Asset identity, initial ownership, and authorization intent. */
public interface AssetRegistrationCommand {

    UUID register(NewAsset command);

    record NewAsset(
            UUID organizationId,
            AssetType type,
            String namespace,
            String slug,
            UUID knowledgeSpaceId,
            PrincipalRef owner,
            UUID assignedByUserId) {

        public NewAsset {
            Objects.requireNonNull(organizationId, "organizationId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(slug, "slug");
            Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(assignedByUserId, "assignedByUserId");
        }
    }
}
