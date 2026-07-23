package com.orgmemory.core.knowledge;

import java.util.UUID;

record ConnectorRevisionDraft(
        UUID sourceObjectId,
        UUID sourceRevisionId,
        long revisionNumber,
        boolean existing) {
}
