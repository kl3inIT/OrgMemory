package com.orgmemory.core.knowledge.sourceledger;

import java.util.UUID;

/**
 * Read-only current ACL head for a source identity, including the content revision needed for
 * compare-and-set ingestion decisions.
 */
public record SourceHeadView(
        UUID rawSourceObjectId,
        UUID currentSnapshotId,
        long aclGeneration,
        String currentContentRevision) {
}
