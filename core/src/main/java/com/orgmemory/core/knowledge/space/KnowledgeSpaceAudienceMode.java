package com.orgmemory.core.knowledge.space;

/** Durable audience promise for a Knowledge Space. */
public enum KnowledgeSpaceAudienceMode {
    /** Every current organization member is in the Space audience. */
    ORGANIZATION,
    /** Every current member of one owning department is in the Space audience. */
    DEPARTMENT,
    /** No implicit audience; viewers are granted explicitly. */
    RESTRICTED_CUSTOM
}
