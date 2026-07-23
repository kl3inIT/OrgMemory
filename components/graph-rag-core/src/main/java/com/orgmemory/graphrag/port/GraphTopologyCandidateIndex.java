package com.orgmemory.graphrag.port;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Expands already-authorized graph seeds into topology candidates.
 *
 * <p>Returned identifiers are candidates only. Callers must load evidence
 * through {@link GraphProjectionReader} before using a candidate in context or
 * a citation.
 */
public interface GraphTopologyCandidateIndex {

    List<UUID> expandEntityIds(
            AuthorizedEvidenceScope scope,
            Collection<UUID> seedEntityIds,
            int maximumDepth,
            int limit);
}
