package com.orgmemory.graphrag.curation;

import com.orgmemory.graphrag.model.CanonicalRelation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic read-time alias and suppression semantics.
 *
 * <p>Aliases are resolved before suppressions. Cycles and conflicting active
 * aliases fail closed. Relations collapsed to self-loops are excluded.
 */
public final class EffectiveGraphCuration {

    private final Map<GraphIdentityRef, GraphIdentityRef> terminalAliases;
    private final Set<GraphIdentityRef> suppressed;
    private final List<GraphCurationRecord.CuratedEntity> curatedEntities;
    private final List<GraphCurationRecord.CuratedRelation> curatedRelations;

    private EffectiveGraphCuration(
            Map<GraphIdentityRef, GraphIdentityRef> terminalAliases,
            Set<GraphIdentityRef> suppressed,
            List<GraphCurationRecord.CuratedEntity> curatedEntities,
            List<GraphCurationRecord.CuratedRelation> curatedRelations) {
        this.terminalAliases = Map.copyOf(terminalAliases);
        this.suppressed = Set.copyOf(suppressed);
        this.curatedEntities = List.copyOf(curatedEntities);
        this.curatedRelations = List.copyOf(curatedRelations);
    }

    public static EffectiveGraphCuration from(
            List<? extends GraphCurationRecord> records) {
        Objects.requireNonNull(records, "records");
        Map<GraphIdentityRef, GraphIdentityRef> directAliases =
                new LinkedHashMap<>();
        Set<GraphIdentityRef> suppressions = new HashSet<>();
        List<GraphCurationRecord.CuratedEntity> entities = new ArrayList<>();
        List<GraphCurationRecord.CuratedRelation> relations = new ArrayList<>();
        for (GraphCurationRecord record : records) {
            Objects.requireNonNull(record, "record");
            switch (record) {
                case GraphCurationRecord.IdentityAlias alias -> {
                    GraphIdentityRef previous =
                            directAliases.putIfAbsent(alias.source(), alias.target());
                    if (previous != null && !previous.equals(alias.target())) {
                        throw new InvalidCurationOverlayException(
                                "an identity has conflicting active aliases");
                    }
                }
                case GraphCurationRecord.IdentitySuppression suppression ->
                        suppressions.add(suppression.identity());
                case GraphCurationRecord.CuratedEntity entity -> entities.add(entity);
                case GraphCurationRecord.CuratedRelation relation ->
                        relations.add(relation);
            }
        }
        Map<GraphIdentityRef, GraphIdentityRef> terminal =
                resolveAliases(directAliases);
        Set<GraphIdentityRef> canonicalSuppressions = new HashSet<>();
        for (GraphIdentityRef suppression : suppressions) {
            canonicalSuppressions.add(terminal.getOrDefault(suppression, suppression));
        }
        return new EffectiveGraphCuration(
                terminal,
                canonicalSuppressions,
                latestEntities(entities),
                latestRelations(relations));
    }

    public Optional<GraphIdentityRef> effective(GraphIdentityRef identity) {
        GraphIdentityRef canonical =
                terminalAliases.getOrDefault(
                        Objects.requireNonNull(identity, "identity"), identity);
        return suppressed.contains(canonical)
                ? Optional.empty()
                : Optional.of(canonical);
    }

    public Optional<CanonicalRelation> effective(CanonicalRelation relation) {
        Objects.requireNonNull(relation, "relation");
        Optional<GraphIdentityRef> relationIdentity =
                effective(GraphIdentityRef.relation(relation.id()));
        Optional<GraphIdentityRef> source =
                effective(GraphIdentityRef.entity(relation.sourceEntityId()));
        Optional<GraphIdentityRef> target =
                effective(GraphIdentityRef.entity(relation.targetEntityId()));
        if (relationIdentity.isEmpty() || source.isEmpty() || target.isEmpty()) {
            return Optional.empty();
        }
        UUID sourceId = source.orElseThrow().id();
        UUID targetId = target.orElseThrow().id();
        if (sourceId.equals(targetId)) {
            return Optional.empty();
        }
        return Optional.of(new CanonicalRelation(
                relationIdentity.orElseThrow().id(),
                sourceId,
                targetId,
                relation.orientation()));
    }

    public List<GraphCurationRecord.CuratedEntity> curatedEntities() {
        return curatedEntities;
    }

    public List<GraphCurationRecord.CuratedRelation> curatedRelations() {
        return curatedRelations;
    }

    private static Map<GraphIdentityRef, GraphIdentityRef> resolveAliases(
            Map<GraphIdentityRef, GraphIdentityRef> direct) {
        Map<GraphIdentityRef, GraphIdentityRef> resolved = new HashMap<>();
        for (GraphIdentityRef source : direct.keySet()) {
            List<GraphIdentityRef> path = new ArrayList<>();
            Set<GraphIdentityRef> seen = new HashSet<>();
            GraphIdentityRef current = source;
            while (direct.containsKey(current)) {
                if (!seen.add(current)) {
                    throw new InvalidCurationOverlayException(
                            "identity aliases must be acyclic");
                }
                path.add(current);
                current = direct.get(current);
            }
            for (GraphIdentityRef visited : path) {
                resolved.put(visited, current);
            }
        }
        return resolved;
    }

    private static List<GraphCurationRecord.CuratedEntity> latestEntities(
            List<GraphCurationRecord.CuratedEntity> records) {
        Map<GraphIdentityRef, GraphCurationRecord.CuratedEntity> latest =
                new HashMap<>();
        records.stream()
                .sorted(Comparator.comparing(
                                (GraphCurationRecord.CuratedEntity record) ->
                                        record.provenance().curatedAt())
                        .thenComparing(GraphCurationRecord.CuratedEntity::id))
                .forEach(record -> latest.put(record.entity(), record));
        return latest.values().stream()
                .sorted(Comparator.comparing(
                        GraphCurationRecord.CuratedEntity::id))
                .toList();
    }

    private static List<GraphCurationRecord.CuratedRelation> latestRelations(
            List<GraphCurationRecord.CuratedRelation> records) {
        Map<GraphIdentityRef, GraphCurationRecord.CuratedRelation> latest =
                new HashMap<>();
        records.stream()
                .sorted(Comparator.comparing(
                                (GraphCurationRecord.CuratedRelation record) ->
                                        record.provenance().curatedAt())
                        .thenComparing(GraphCurationRecord.CuratedRelation::id))
                .forEach(record -> latest.put(record.relation(), record));
        return latest.values().stream()
                .sorted(Comparator.comparing(
                        GraphCurationRecord.CuratedRelation::id))
                .toList();
    }

    public static final class InvalidCurationOverlayException
            extends RuntimeException {

        public InvalidCurationOverlayException(String message) {
            super(Objects.requireNonNull(message, "message"));
        }
    }
}
