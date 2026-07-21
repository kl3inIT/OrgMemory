package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.AccessGate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RotateSourceAclCommand(
        UUID organizationId,
        UUID rawSourceObjectId,
        AclCaptureStatus aclCaptureStatus,
        AccessGate defaultGate,
        Instant aclValidUntil,
        List<SourceAclEntryCommand> aclEntries,
        UUID expectedCurrentSnapshotId) {

    public RotateSourceAclCommand {
        aclEntries = aclEntries == null ? List.of() : List.copyOf(aclEntries);
    }
}
