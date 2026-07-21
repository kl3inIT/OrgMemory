package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.AccessGate;
import java.util.UUID;

record SourceAclEvaluation(
        AccessGate effectiveGate,
        UUID ingestionSnapshotId,
        UUID currentSnapshotId) {
}
