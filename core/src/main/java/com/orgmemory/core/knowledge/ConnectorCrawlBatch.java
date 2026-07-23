package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.UUID;

/**
 * One staging crawl batch for a source connection: the envelope plus the three
 * separately-versioned payload kinds and any tombstones. A batch is the unit the connector
 * ingestion use case reconciles into the governed ledger. The {@code crawlCursor} is an
 * opaque, per-connection idempotency/resume marker owned by the batch producer.
 *
 * @param organizationId     tenant the crawl belongs to
 * @param sourceSystem       the source system id (for example {@code slack})
 * @param sourceConnectionKey the connection/workspace id within that system
 * @param knowledgeSpaceId   the Knowledge Space crawled content is published into
 * @param actorUserId        the connection owner recorded as author, publication owner, and audit actor
 * @param crawlCursor        opaque per-connection cursor for idempotency and resume
 * @param versions           the declared payload versions (rejected if unsupported)
 * @param identities         observed users and groups (with group membership)
 * @param contents           content objects found by this crawl
 * @param permissions        per-object access control lists
 * @param tombstones         objects removed at the source since the last crawl
 */
public record ConnectorCrawlBatch(
        UUID organizationId,
        String sourceSystem,
        String sourceConnectionKey,
        UUID knowledgeSpaceId,
        UUID actorUserId,
        String crawlCursor,
        ConnectorContractVersions versions,
        List<ConnectorIdentityItem> identities,
        List<ConnectorContentItem> contents,
        List<ConnectorPermissionItem> permissions,
        List<ConnectorTombstone> tombstones) {

    public ConnectorCrawlBatch {
        if (organizationId == null) {
            throw new IllegalArgumentException("connector batch organizationId is required");
        }
        if (knowledgeSpaceId == null) {
            throw new IllegalArgumentException("connector batch knowledgeSpaceId is required");
        }
        if (actorUserId == null) {
            throw new IllegalArgumentException("connector batch actorUserId is required");
        }
        sourceSystem = requireText(sourceSystem, "sourceSystem");
        sourceConnectionKey = requireText(sourceConnectionKey, "sourceConnectionKey");
        crawlCursor = requireText(crawlCursor, "crawlCursor");
        if (versions == null) {
            throw new IllegalArgumentException("connector batch versions are required");
        }
        identities = identities == null ? List.of() : List.copyOf(identities);
        contents = contents == null ? List.of() : List.copyOf(contents);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        tombstones = tombstones == null ? List.of() : List.copyOf(tombstones);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("connector batch " + field + " is required");
        }
        return value.trim();
    }
}
