package com.orgmemory.core.knowledge.connector;


import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One staging crawl batch for a source connection: the envelope plus the four
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
 * @param memberships        independently captured source-group member lists
 * @param contents           content objects found by this crawl
 * @param permissions        per-object access control lists
 * @param tombstones         objects removed at the source since the last crawl
 * @param componentStates    independently checkpointed component cursors and completeness
 * @param crawlComplete      whether {@code contents} and {@code permissions} together enumerate
 *                           every object this connection currently has at the source. Only a
 *                           batch that says so may retire what it did not mention, because
 *                           pruning against a partial, interrupted, or single-channel crawl
 *                           would retire the whole connection. Absent means no.
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
        List<ConnectorMembershipItem> memberships,
        List<ConnectorContentItem> contents,
        List<ConnectorPermissionItem> permissions,
        List<ConnectorTombstone> tombstones,
        List<ConnectorComponentState> componentStates,
        boolean crawlComplete) {

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
        memberships = memberships == null ? List.of() : List.copyOf(memberships);
        contents = contents == null ? List.of() : List.copyOf(contents);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        tombstones = tombstones == null ? List.of() : List.copyOf(tombstones);
        componentStates = componentStates == null ? List.of() : List.copyOf(componentStates);
        if (componentStates.isEmpty()) {
            throw new IllegalArgumentException("connector batch componentStates are required");
        }
        EnumSet<ConnectorSyncComponent> declared = EnumSet.noneOf(ConnectorSyncComponent.class);
        for (ConnectorComponentState state : componentStates) {
            if (state == null || !declared.add(state.component())) {
                throw new IllegalArgumentException(
                        "connector batch componentStates must contain each component at most once");
            }
        }
        requireComponent(declared, ConnectorSyncComponent.CONTENT,
                !contents.isEmpty() || !tombstones.isEmpty());
        requireComponent(declared, ConnectorSyncComponent.PERMISSION,
                !permissions.isEmpty() || !contents.isEmpty());
        requireComponent(declared, ConnectorSyncComponent.MEMBERSHIP,
                !memberships.isEmpty());
    }

    /** Compatibility constructor for callers that still use one combined source cursor. */
    public ConnectorCrawlBatch(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            UUID knowledgeSpaceId,
            UUID actorUserId,
            String crawlCursor,
            ConnectorContractVersions versions,
            List<ConnectorIdentityItem> identities,
            List<ConnectorMembershipItem> memberships,
            List<ConnectorContentItem> contents,
            List<ConnectorPermissionItem> permissions,
            List<ConnectorTombstone> tombstones,
            boolean crawlComplete) {
        this(
                organizationId,
                sourceSystem,
                sourceConnectionKey,
                knowledgeSpaceId,
                actorUserId,
                crawlCursor,
                versions,
                identities,
                memberships,
                contents,
                permissions,
                tombstones,
                inferredStates(crawlCursor, memberships),
                crawlComplete);
    }

    /** A batch that makes no claim about completeness, and therefore retires nothing. */
    public ConnectorCrawlBatch(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            UUID knowledgeSpaceId,
            UUID actorUserId,
            String crawlCursor,
            ConnectorContractVersions versions,
            List<ConnectorIdentityItem> identities,
            List<ConnectorMembershipItem> memberships,
            List<ConnectorContentItem> contents,
            List<ConnectorPermissionItem> permissions,
            List<ConnectorTombstone> tombstones) {
        this(
                organizationId,
                sourceSystem,
                sourceConnectionKey,
                knowledgeSpaceId,
                actorUserId,
                crawlCursor,
                versions,
                identities,
                memberships,
                contents,
                permissions,
                tombstones,
                inferredStates(crawlCursor, memberships),
                false);
    }

    public Set<ConnectorSyncComponent> components() {
        EnumSet<ConnectorSyncComponent> components =
                EnumSet.noneOf(ConnectorSyncComponent.class);
        componentStates.forEach(state -> components.add(state.component()));
        return Set.copyOf(components);
    }

    public ConnectorComponentState componentState(ConnectorSyncComponent component) {
        return componentStates.stream()
                .filter(state -> state.component() == component)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "connector batch does not declare component " + component));
    }

    /** The objects this crawl saw, which is the set a complete crawl is diffed against. */
    Set<String> crawledObjectIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (ConnectorContentItem content : contents) {
            ids.add(content.externalObjectId());
        }
        for (ConnectorPermissionItem permission : permissions) {
            ids.add(permission.externalObjectId());
        }
        return ids;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("connector batch " + field + " is required");
        }
        return value.trim();
    }

    private static void requireComponent(
            Set<ConnectorSyncComponent> declared,
            ConnectorSyncComponent component,
            boolean required) {
        if (required && !declared.contains(component)) {
            throw new IllegalArgumentException(
                    "connector batch payload requires component " + component);
        }
    }

    private static List<ConnectorComponentState> inferredStates(
            String cursor, List<ConnectorMembershipItem> memberships) {
        List<ConnectorMembershipItem> safeMemberships =
                memberships == null ? List.of() : memberships;
        boolean incompleteMembership = safeMemberships.stream()
                .anyMatch(item -> item.captureStatus() == ConnectorCaptureStatus.INCOMPLETE);
        String reason = safeMemberships.stream()
                .filter(item -> item.captureStatus() == ConnectorCaptureStatus.INCOMPLETE)
                .map(ConnectorMembershipItem::incompleteReason)
                .findFirst()
                .orElse(null);
        return List.of(
                ConnectorComponentState.complete(ConnectorSyncComponent.CONTENT, cursor),
                ConnectorComponentState.complete(ConnectorSyncComponent.PERMISSION, cursor),
                incompleteMembership
                        ? ConnectorComponentState.incomplete(
                                ConnectorSyncComponent.MEMBERSHIP, cursor, reason)
                        : ConnectorComponentState.complete(
                                ConnectorSyncComponent.MEMBERSHIP, cursor));
    }
}
