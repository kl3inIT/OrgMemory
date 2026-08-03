package com.orgmemory.core.assetregistry;

enum SkillPackageCleanupOutcome {
    DELETED,
    RETAINED_BY_IMMUTABLE_REFERENCE,
    RETRY_SCHEDULED,
    ALREADY_RESOLVED
}
