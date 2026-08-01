package com.orgmemory.graphrag.query;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.storage.AuthorizedGraphTraversalSource;
import com.orgmemory.graphrag.storage.AuthorizedGraphTraversalSource.IncidentRelationPage;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Core-owned, level-synchronous authorized graph traversal policy. */
public final class AuthorizedGraphTraversal {

    static final int DEFAULT_RELATION_PAGE_SIZE = 512;

    private final AuthorizedGraphTraversalSource source;
    private final int relationPageSize;

    public AuthorizedGraphTraversal(AuthorizedGraphTraversalSource source) {
        this(source, DEFAULT_RELATION_PAGE_SIZE);
    }

    AuthorizedGraphTraversal(
            AuthorizedGraphTraversalSource source,
            int relationPageSize) {
        this.source = Objects.requireNonNull(source, "source");
        if (relationPageSize <= 0) {
            throw new IllegalArgumentException("relationPageSize must be positive");
        }
        this.relationPageSize = relationPageSize;
    }

    public List<UUID> expandEntityIds(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> seedEntityIds,
            int maximumDepth,
            int limit) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(snapshot, "snapshot");
        List<UUID> seeds = requireIds(seedEntityIds);
        requireNonNegative(maximumDepth, "maximumDepth");
        requireNonNegative(limit, "limit");

        source.validateSnapshot(scope, snapshot);
        if (limit == 0 || seeds.isEmpty()) {
            return List.of();
        }

        TreeSet<UUID> requestedSeeds = orderedIds();
        requestedSeeds.addAll(seeds);
        TreeSet<UUID> visibleSeeds = visibleRequestedIds(
                scope,
                snapshot,
                requestedSeeds);
        if (visibleSeeds.isEmpty()) {
            return List.of();
        }

        List<UUID> result = new ArrayList<>(Math.min(limit, visibleSeeds.size()));
        appendWithinLimit(result, visibleSeeds, limit);
        if (result.size() == limit || maximumDepth == 0) {
            return List.copyOf(result);
        }

        Set<UUID> visited = new HashSet<>(visibleSeeds);
        Set<UUID> frontier = Set.copyOf(visibleSeeds);
        int depth = 0;
        while (!frontier.isEmpty() && depth < maximumDepth && result.size() < limit) {
            TreeSet<UUID> candidates = relationCandidates(
                    scope,
                    snapshot,
                    frontier);
            candidates.removeAll(visited);
            TreeSet<UUID> next = visibleRequestedIds(
                    scope,
                    snapshot,
                    candidates);
            appendWithinLimit(result, next, limit);
            visited.addAll(next);
            frontier = Set.copyOf(next);
            depth++;
        }
        return List.copyOf(result);
    }

    private TreeSet<UUID> relationCandidates(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Set<UUID> frontier) {
        TreeSet<UUID> candidates = orderedIds();
        Set<UUID> seenRelations = new HashSet<>();
        UUID cursor = null;
        while (true) {
            IncidentRelationPage page = Objects.requireNonNull(
                    source.loadIncidentRelationPage(
                            scope,
                            snapshot,
                            frontier,
                            cursor,
                            relationPageSize),
                    "incident relation page");
            if (page.relations().size() > relationPageSize) {
                throw new IllegalStateException(
                        "incident relation page exceeded the requested page size");
            }
            for (CanonicalRelation relation : page.relations()) {
                if (cursor != null
                        && AuthorizedGraphTraversalSource.CANONICAL_UUID_ORDER.compare(
                                relation.id(), cursor) <= 0) {
                    throw new IllegalStateException(
                            "incident relation page did not advance past its cursor");
                }
                if (!seenRelations.add(relation.id())) {
                    throw new IllegalStateException(
                            "incident relation page repeated a relation");
                }
                if (frontier.stream().noneMatch(relation::isIncidentTo)) {
                    throw new IllegalStateException(
                            "incident relation page returned a relation outside the frontier");
                }
                candidates.add(relation.sourceEntityId());
                candidates.add(relation.targetEntityId());
            }
            UUID nextCursor = page.nextCursor();
            if (nextCursor == null) {
                return candidates;
            }
            if (cursor != null
                    && AuthorizedGraphTraversalSource.CANONICAL_UUID_ORDER.compare(
                            nextCursor, cursor) <= 0) {
                throw new IllegalStateException(
                        "incident relation cursor did not advance");
            }
            cursor = nextCursor;
        }
    }

    private TreeSet<UUID> visibleRequestedIds(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> requestedIds) {
        if (requestedIds.isEmpty()) {
            return orderedIds();
        }
        Set<UUID> requested = Set.copyOf(requestedIds);
        TreeSet<UUID> visible = orderedIds();
        for (CanonicalEntity entity : source.loadEntities(
                scope,
                snapshot,
                requested)) {
            Objects.requireNonNull(entity, "loaded entities must not contain null");
            if (!requested.contains(entity.id())) {
                throw new IllegalStateException(
                        "graph traversal source returned an unrequested entity");
            }
            visible.add(entity.id());
        }
        return visible;
    }

    private static void appendWithinLimit(
            List<UUID> result,
            Collection<UUID> ids,
            int limit) {
        for (UUID id : ids) {
            if (result.size() == limit) {
                return;
            }
            result.add(id);
        }
    }

    private static List<UUID> requireIds(Collection<UUID> ids) {
        Objects.requireNonNull(ids, "seedEntityIds");
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        for (UUID id : ids) {
            result.add(Objects.requireNonNull(id, "seedEntityIds must not contain null"));
        }
        return List.copyOf(result);
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static TreeSet<UUID> orderedIds() {
        return new TreeSet<>(AuthorizedGraphTraversalSource.CANONICAL_UUID_ORDER);
    }
}
