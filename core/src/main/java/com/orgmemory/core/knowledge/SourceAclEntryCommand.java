package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.AccessGate;

public record SourceAclEntryCommand(
        SourcePrincipalType principalType,
        String principalKey,
        AccessGate gate) {
}
