package com.orgmemory.core.knowledge.evidence;

import java.util.Optional;
import java.util.UUID;

/** Resolves one exact organization-scoped Source revision without exporting persistence. */
public interface GovernedEvidenceQuery {

    Optional<GovernedEvidenceRef> find(
            UUID organizationId,
            UUID sourceObjectId,
            UUID sourceRevisionId);
}
