package com.orgmemory.core.knowledge.acl;

import java.util.Objects;
import java.util.UUID;

/** Source identity required when advancing the ACL head. */
public record SourceAclTarget(
        UUID rawSourceObjectId,
        UUID organizationId,
        String sourceSystem,
        String sourceConnectionKey,
        String externalObjectId) {

    public SourceAclTarget {
        Objects.requireNonNull(rawSourceObjectId, "rawSourceObjectId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(sourceSystem, "sourceSystem");
        Objects.requireNonNull(sourceConnectionKey, "sourceConnectionKey");
        Objects.requireNonNull(externalObjectId, "externalObjectId");
    }
}
