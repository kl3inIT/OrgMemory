package com.orgmemory.core.knowledge;

import java.util.List;

/**
 * The per-object access control list a crawl found, independent of the object's content so
 * permissions can re-crawl on their own cadence. Each grant names a principal the source
 * authorizes; the connector seals these as source ACL evidence for one generation.
 *
 * @param externalObjectId the object these grants apply to
 * @param grants           the source-declared grants (may be empty, which grants no one)
 */
public record ConnectorPermissionItem(String externalObjectId, List<ConnectorAclGrant> grants) {

    public ConnectorPermissionItem {
        if (externalObjectId == null || externalObjectId.isBlank()) {
            throw new IllegalArgumentException("connector permission externalObjectId is required");
        }
        externalObjectId = externalObjectId.trim();
        grants = grants == null ? List.of() : List.copyOf(grants);
    }
}
