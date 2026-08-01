package com.orgmemory.core.knowledge.sourceledger;

import java.util.UUID;

/** Stable Source Ledger identity exposed without its persistence entity. */
public record SourceInventoryRef(UUID sourceObjectId) {}
