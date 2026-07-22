package com.orgmemory.core.knowledge;

import java.util.UUID;

/**
 * A read-only view of the current source ACL head for a source object identity, used by the
 * connector to decide whether to register a new source (no head yet) or rotate the ACL of an
 * existing one, and to supply the compare-and-set expectation.
 */
record ConnectorHeadView(
        UUID rawSourceObjectId, UUID currentSnapshotId, long aclGeneration, String currentContentRevision) {
}
