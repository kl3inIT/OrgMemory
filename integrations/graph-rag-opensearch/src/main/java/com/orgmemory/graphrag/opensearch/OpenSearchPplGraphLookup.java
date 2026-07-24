package com.orgmemory.graphrag.opensearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.opensearch.client.opensearch.generic.Requests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional PPL graphLookup accelerator.
 *
 * <p>The lookup index is physical-snapshot scoped and the PPL {@code filter}
 * clause repeats tenant, batch, generation, and authorized-asset constraints
 * at every BFS level. Returned identifiers are still re-read through
 * {@link OpenSearchGraphStore}'s authorization-filtered entity path.
 */
final class OpenSearchPplGraphLookup {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OpenSearchPplGraphLookup.class);
    private static final int MAXIMUM_INLINE_ASSETS = 1_000;

    private final OpenSearchOperations operations;
    private final OpenSearchIndexNames indexes;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final AtomicReference<Availability> availability =
            new AtomicReference<>(Availability.UNKNOWN);
    private final AtomicLong successfulExecutions = new AtomicLong();

    OpenSearchPplGraphLookup(
            OpenSearchOperations operations,
            OpenSearchIndexNames indexes,
            ObjectMapper objectMapper,
            boolean enabled) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.indexes = Objects.requireNonNull(indexes, "indexes");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.enabled = enabled;
    }

    Optional<List<UUID>> expand(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Set<UUID> seedEntityIds,
            int maximumDepth,
            int limit) {
        if (!enabled
                || availability.get() == Availability.UNAVAILABLE
                || maximumDepth <= 0
                || seedEntityIds.isEmpty()
                || scope.authorizedAssetIds().isEmpty()
                || scope.authorizedAssetIds().size() > MAXIMUM_INLINE_ASSETS) {
            return Optional.empty();
        }
        String entities = indexes.graphEntities(snapshot.batchId());
        String relations = indexes.graphRelations(snapshot.batchId());
        if (!operations.indexExists(entities) || !operations.indexExists(relations)) {
            return Optional.empty();
        }
        String evidenceFilter = evidenceFilter(scope, snapshot);
        String seeds = seedEntityIds.stream()
                .sorted()
                .map(id -> "entity_id = '" + id + "'")
                .reduce((left, right) -> left + " or " + right)
                .orElseThrow();
        int pplDepth = Math.max(0, maximumDepth - 1);
        String query = "source = "
                + entities
                + " | where ("
                + seeds
                + ") and ("
                + evidenceFilter
                + ") | graphLookup "
                + relations
                + " start=entity_id"
                + " edge=target_entity_id<->source_entity_id"
                + " maxDepth="
                + pplDepth
                + " depthField=_depth"
                + " usePIT=true"
                + " filter=("
                + evidenceFilter
                + ") as connected_edges";
        try {
            var request = Requests.builder()
                    .method("POST")
                    .endpoint("/_plugins/_ppl")
                    .json(objectMapper.writeValueAsString(java.util.Map.of("query", query)))
                    .build();
            try (var response = operations.client().generic().execute(request)) {
                if (response.getStatus() < 200 || response.getStatus() >= 300) {
                    if (isDefinitivelyUnavailable(response.getStatus())) {
                        availability.set(Availability.UNAVAILABLE);
                    }
                    return Optional.empty();
                }
                JsonNode body = response.getBody()
                        .map(raw -> {
                            try {
                                return objectMapper.readTree(raw.bodyAsBytes());
                            } catch (java.io.IOException exception) {
                                throw new OpenSearchProjectionException(
                                        "OpenSearch returned invalid PPL JSON",
                                        exception);
                            }
                        })
                        .orElse(null);
                List<UUID> expanded =
                        parse(body, seedEntityIds, limit).orElse(null);
                if (expanded == null) {
                    return Optional.empty();
                }
                availability.set(Availability.AVAILABLE);
                successfulExecutions.incrementAndGet();
                return Optional.of(expanded);
            }
        } catch (Exception exception) {
            // A transport error or transient server failure must not
            // permanently disable the accelerator. The caller safely falls
            // back to bounded BFS and a later request may probe PPL again.
            LOGGER.debug(
                    "OpenSearch PPL accelerator failed; falling back to bounded BFS",
                    exception);
            return Optional.empty();
        }
    }

    long successfulExecutions() {
        return successfulExecutions.get();
    }

    private static boolean isDefinitivelyUnavailable(int status) {
        return status == 400 || status == 404 || status == 405;
    }

    private static Optional<List<UUID>> parse(
            JsonNode body,
            Set<UUID> seeds,
            int limit) {
        if (body == null || !body.isObject()) {
            return Optional.empty();
        }
        int edgesIndex = -1;
        JsonNode schema = body.path("schema");
        if (schema.isArray()) {
            for (int index = 0; index < schema.size(); index++) {
                if ("connected_edges".equals(schema.get(index).path("name").asText())) {
                    edgesIndex = index;
                    break;
                }
            }
        }
        if (edgesIndex < 0 || !body.path("datarows").isArray()) {
            return Optional.empty();
        }
        List<JsonNode> edges = new ArrayList<>();
        for (JsonNode row : body.path("datarows")) {
            if (!row.isArray() || row.size() <= edgesIndex) {
                return Optional.empty();
            }
            JsonNode connected = row.get(edgesIndex);
            if (!connected.isArray()) {
                return Optional.empty();
            }
            connected.forEach(edges::add);
        }
        if (edges.stream().anyMatch(edge -> !edge.isObject())) {
            return Optional.empty();
        }
        edges.sort(Comparator
                .comparingInt((JsonNode edge) -> edge.path("_depth").asInt(Integer.MAX_VALUE))
                .thenComparing(edge -> edge.path("source_entity_id").asText())
                .thenComparing(edge -> edge.path("target_entity_id").asText()));
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        seeds.stream().sorted().forEach(result::add);
        try {
            for (JsonNode edge : edges) {
                addUuid(result, edge.path("source_entity_id").asText(null), limit);
                addUuid(result, edge.path("target_entity_id").asText(null), limit);
                if (result.size() >= limit) {
                    break;
                }
            }
        } catch (IllegalArgumentException invalidIdentifier) {
            return Optional.empty();
        }
        return Optional.of(result.stream().limit(limit).toList());
    }

    private static void addUuid(
            Set<UUID> target,
            String value,
            int limit) {
        if (value != null && target.size() < limit) {
            target.add(UUID.fromString(value));
        }
    }

    private static String evidenceFilter(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot) {
        String assets = scope.authorizedAssetIds().stream()
                .sorted()
                .map(id -> "'" + id + "'")
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
        return OpenSearchProjectionCodec.ORGANIZATION_ID
                + " = '"
                + scope.organizationId()
                + "' and "
                + OpenSearchProjectionCodec.BATCH_ID
                + " = '"
                + snapshot.batchId()
                + "' and "
                + OpenSearchProjectionCodec.GENERATION
                + " = "
                + snapshot.generation()
                + " and "
                + OpenSearchProjectionCodec.ASSET_ID
                + " in ("
                + assets
                + ")";
    }

    private enum Availability {
        UNKNOWN,
        AVAILABLE,
        UNAVAILABLE
    }
}
