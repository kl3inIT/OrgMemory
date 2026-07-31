package com.orgmemory.core.knowledge.connector;

/** Independently checkpointed connector work carried by a crawl batch. */
public enum ConnectorSyncComponent {
    CONTENT,
    PERMISSION,
    MEMBERSHIP
}
