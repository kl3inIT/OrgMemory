package com.orgmemory.graphrag.curation;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.util.List;
import java.util.Objects;

/**
 * Permission-aware curation reader. Implementations filter governing evidence
 * before returning an overlay; callers must not pass an unscoped record set.
 */
public interface GraphCurationOverlay {

    List<GraphCurationRecord> load(
            AuthorizedEvidenceScope scope, ProjectionNamespace namespace);

    static void requireMatchingScope(
            AuthorizedEvidenceScope scope, ProjectionNamespace namespace) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(namespace, "namespace");
        if (!scope.organizationId().equals(namespace.organizationId())) {
            throw new IllegalArgumentException(
                    "authorization scope and curation namespace must match");
        }
    }
}
