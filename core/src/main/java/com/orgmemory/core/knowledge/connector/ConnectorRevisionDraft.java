package com.orgmemory.core.knowledge.connector;

import java.util.UUID;

record ConnectorRevisionDraft(
        UUID sourceObjectId,
        UUID sourceRevisionId,
        long revisionNumber,
        boolean existing) {
}
