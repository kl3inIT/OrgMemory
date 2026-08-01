package com.orgmemory.core.knowledge.sourceledger;

import java.util.UUID;

/** Stable staged-revision facts exposed without Source Ledger persistence types. */
public record SourceRevisionDraftRef(
        UUID sourceObjectId,
        UUID sourceRevisionId,
        long revisionNumber,
        boolean published) {}
