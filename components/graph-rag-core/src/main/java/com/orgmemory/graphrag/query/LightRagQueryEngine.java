package com.orgmemory.graphrag.query;

import com.orgmemory.graphrag.authorization.PermissionScopedGraphMerger;
import com.orgmemory.graphrag.authorization.PermissionScopedGraphView;
import com.orgmemory.graphrag.chunking.TextEmbeddingPort;
import com.orgmemory.graphrag.chunking.TextTokenizer;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.observability.GraphRagEventSink;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Framework-neutral executable LightRAG v1.5.4 query runtime.
 *
 * <p>Every projection read is both authorization-scoped and snapshot-pinned.
 * The application shell still owns current ACL resolution and final citation
 * recheck before egress.
 */
public final class LightRagQueryEngine {

    private final AuthorizedQueryProjection projection;
    private final LightRagKeywordPlanner keywordPlanner;
    private final TextEmbeddingPort embeddings;
    private final TextTokenizer tokenizer;
    private final LightRagGroundingAssembler groundingAssembler;
    private final ChunkReranker reranker;
    private final QueryAnswerModel answerModel;

    public LightRagQueryEngine(
            AuthorizedQueryProjection projection,
            LightRagKeywordPlanner keywordPlanner,
            TextEmbeddingPort embeddings,
            TextTokenizer tokenizer,
            ChunkReranker reranker,
            QueryAnswerModel answerModel) {
        this.projection = Objects.requireNonNull(projection, "projection");
        this.keywordPlanner = Objects.requireNonNull(keywordPlanner, "keywordPlanner");
        this.embeddings = Objects.requireNonNull(embeddings, "embeddings");
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
        this.groundingAssembler = new LightRagGroundingAssembler(tokenizer);
        this.reranker = Objects.requireNonNull(reranker, "reranker");
        this.answerModel = Objects.requireNonNull(answerModel, "answerModel");
    }

    public LightRagQueryResult execute(LightRagQueryRequest request) {
        return executePrepared(request, prepare(request));
    }

    /**
     * Performs keyword planning and embedding exactly once for one logical
     * query. The returned value contains no authorization or evidence state.
     */
    public LightRagPreparedQuery prepare(LightRagQueryRequest request) {
        Objects.requireNonNull(request, "request");
        long keywordStartedAt = System.nanoTime();
        LightRagKeywordPlanner.PlanningResult keywordPlanning =
                request.options().mode().usesGraph()
                ? keywordPlanner.planWithTrace(
                        request.query(),
                        request.trustedKeywords(),
                        request.scope().organizationId(),
                        request.options().mode().name())
                : new LightRagKeywordPlanner.PlanningResult(
                        KeywordPlan.empty(KeywordPlan.Source.MODEL),
                        GraphRagEventSink.CacheStatus.BYPASS);
        Duration keywordDuration = Duration.ofNanos(Math.max(
                0,
                System.nanoTime() - keywordStartedAt));
        KeywordPlan keywords = keywordPlanning.plan();
        long embeddingStartedAt = System.nanoTime();
        EmbeddingPlan embeddingPlan =
                request.options().mode() == LightRagQueryMode.BYPASS
                        ? new EmbeddingPlan(List.of(), null, null, null)
                        : embed(request, keywords);
        Duration embeddingDuration = Duration.ofNanos(Math.max(
                0,
                System.nanoTime() - embeddingStartedAt));
        return new LightRagPreparedQuery(
                request.query(),
                request.options(),
                request.embeddingProfileId(),
                request.embeddingDimensions(),
                keywords,
                embeddingPlan.inputs(),
                embeddingPlan.query(),
                embeddingPlan.lowLevel(),
                embeddingPlan.highLevel(),
                request.conversationHistory(),
                keywordDuration,
                embeddingDuration,
                keywordPlanning.cacheStatus(),
                keywordPlanner.modelRouteFingerprint());
    }

    /**
     * Executes snapshot-scoped retrieval from an already prepared query.
     * Authorization scope and publication snapshot are still request-specific.
     */
    public LightRagQueryResult executePrepared(
            LightRagQueryRequest request,
            LightRagPreparedQuery prepared) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(prepared, "prepared").requireMatches(request);
        if (request.options().mode() == LightRagQueryMode.BYPASS) {
            return bypass(request);
        }

        KeywordPlan keywords = prepared.keywords();
        if (request.options().mode().usesGraph()
                && !request.options().mode().usesChunkSeeds()
                && keywords.empty()) {
            return noResults(request, keywords, List.of(), "keywords_empty");
        }

        Branch local = request.options().mode().usesEntitySeeds()
                ? localBranch(request, prepared.lowLevelEmbedding())
                : Branch.empty();
        Branch global = request.options().mode().usesRelationSeeds()
                ? globalBranch(request, prepared.highLevelEmbedding())
                : Branch.empty();

        List<RankedItem<PermissionScopedGraphView.EntityView>> entities =
                mergeRanked(local.entities(), global.entities(), request.options().topK());
        List<RankedItem<PermissionScopedGraphView.RelationView>> relations =
                mergeRanked(local.relations(), global.relations(), request.options().topK());

        List<ChunkState> entityChunks = supportChunks(
                request,
                entities.stream().map(RankedItem::value).toList(),
                LightRagQueryResult.Origin.ENTITY,
                Set.of(),
                prepared.queryEmbedding());
        Set<UUID> entityChunkIds =
                entityChunks.stream().map(state -> state.chunk().id()).collect(Collectors.toSet());
        List<ChunkState> relationChunks = supportChunks(
                request,
                relations.stream().map(RankedItem::value).toList(),
                LightRagQueryResult.Origin.RELATION,
                entityChunkIds,
                prepared.queryEmbedding());
        List<ChunkState> vectorChunks = request.options().mode().usesChunkSeeds()
                ? vectorChunks(request, prepared.queryEmbedding())
                : List.of();
        List<ChunkState> chunks =
                interleaveChunks(vectorChunks, entityChunks, relationChunks);

        RerankOutcome reranked = rerank(request, chunks);
        return assemble(
                request,
                keywords,
                prepared.embeddingInputs(),
                local.seedCount(),
                global.seedCount(),
                vectorChunks.size(),
                entities,
                relations,
                reranked);
    }

    /**
     * Applies one final budget across permission-equivalent projection namespaces.
     *
     * <p>The application shell calls this before closing and rechecking the
     * complete evidence set. Rendering the returned grounding again is
     * deterministic and performs no provider or storage effect.
     */
    public LightRagGroundingAssembler.PreparedGrounding consolidateGrounding(
            String query,
            LightRagQueryRequest.Options options,
            List<LightRagGrounding> groundings) {
        return groundingAssembler.consolidate(query, options, groundings);
    }

    public LightRagGroundingAssembler.PreparedGrounding renderGrounding(
            String query,
            LightRagQueryRequest.Options options,
            LightRagGrounding grounding) {
        return groundingAssembler.render(query, options, grounding);
    }

    private LightRagQueryResult bypass(LightRagQueryRequest request) {
        String prompt = """
                Answer the user's query directly. Follow the requested response style: %s.
                Additional user instruction: %s

                ---User Query---
                %s
                """.formatted(
                request.options().responseType(),
                optionalInstruction(request.options().userInstruction()),
                request.query());
        QueryAnswerModel.Response response = answerModel.answer(new QueryAnswerModel.Request(
                request.query(),
                prompt,
                request.conversationHistory(),
                request.options().streaming()));
        LightRagQueryResult.Trace trace = trace(
                request,
                KeywordPlan.empty(KeywordPlan.Source.TRUSTED_CALLER),
                List.of(),
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                Duration.ZERO,
                List.of(),
                "");
        return new LightRagQueryResult(
                LightRagQueryResult.Status.SUCCESS,
                "",
                prompt,
                answer(response),
                List.of(),
                trace);
    }

    private EmbeddingPlan embed(LightRagQueryRequest request, KeywordPlan keywords) {
        List<String> inputs = new ArrayList<>(3);
        Map<Purpose, Integer> offsets = new LinkedHashMap<>();
        boolean queryVectorNeeded = request.options().mode().usesChunkSeeds()
                || request.options().mode().usesGraph()
                        && request.options().relatedChunkSelection()
                                == LightRagQueryRequest.RelatedChunkSelection.VECTOR;
        if (queryVectorNeeded) {
            offsets.put(Purpose.QUERY, inputs.size());
            inputs.add(request.query());
        }
        if (request.options().mode().usesEntitySeeds() && !keywords.lowLevel().isEmpty()) {
            offsets.put(Purpose.LOW_LEVEL, inputs.size());
            inputs.add(keywords.joinedLowLevel());
        }
        if (request.options().mode().usesRelationSeeds() && !keywords.highLevel().isEmpty()) {
            offsets.put(Purpose.HIGH_LEVEL, inputs.size());
            inputs.add(keywords.joinedHighLevel());
        }
        if (inputs.isEmpty()) {
            return new EmbeddingPlan(List.of(), null, null, null);
        }
        List<FloatVector> vectors = embeddings.embedAll(inputs);
        if (vectors.size() != inputs.size()) {
            throw new IllegalStateException("embedding adapter returned an incomplete batch");
        }
        vectors.forEach(vector -> {
            if (vector.dimensions() != request.embeddingDimensions()) {
                throw new IllegalStateException(
                        "query embedding dimensions do not match the pinned profile");
            }
        });
        return new EmbeddingPlan(
                List.copyOf(inputs),
                vector(offsets, vectors, Purpose.QUERY),
                vector(offsets, vectors, Purpose.LOW_LEVEL),
                vector(offsets, vectors, Purpose.HIGH_LEVEL));
    }

    private Branch localBranch(LightRagQueryRequest request, FloatVector lowLevelVector) {
        if (lowLevelVector == null) {
            return Branch.empty();
        }
        var search = vectorSearch(request, lowLevelVector, request.options().topK());
        List<RankedItem<CanonicalEntity>> seeds =
                projection.searchEntities(request.scope(), request.snapshot(), search);
        if (seeds.isEmpty()) {
            return Branch.empty();
        }
        LinkedHashSet<UUID> entityIds = seeds.stream()
                .map(item -> item.value().id())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (request.options().maximumGraphDepth() > 0) {
            entityIds.addAll(projection.expandEntityIds(
                    request.scope(),
                    request.snapshot(),
                    entityIds,
                    request.options().maximumGraphDepth(),
                    request.options().topK() * 4));
        }
        List<CanonicalRelation> incident = projection.loadIncidentRelations(
                request.scope(),
                request.snapshot(),
                entityIds,
                request.options().topK() * 4);
        incident.forEach(relation -> {
            entityIds.add(relation.sourceEntityId());
            entityIds.add(relation.targetEntityId());
        });
        PermissionScopedGraphView view = scopedView(
                request,
                entityIds,
                incident.stream().map(CanonicalRelation::id).toList());
        Map<UUID, PermissionScopedGraphView.EntityView> entityViews =
                index(view.entities(), item -> item.entity().id());
        Map<UUID, PermissionScopedGraphView.RelationView> relationViews =
                index(view.relations(), item -> item.relation().id());
        Map<UUID, Double> seedScores = seeds.stream().collect(Collectors.toMap(
                item -> item.value().id(),
                RankedItem::score,
                Math::max,
                LinkedHashMap::new));
        Map<UUID, Long> degrees = projection.loadVisibleEntityDegrees(
                request.scope(), request.snapshot(), entityIds);
        List<RankedItem<PermissionScopedGraphView.EntityView>> orderedEntities =
                entityViews.values().stream()
                        .sorted(Comparator
                                .comparingDouble((PermissionScopedGraphView.EntityView item) ->
                                        seedScores.getOrDefault(item.entity().id(), 0.0))
                                .reversed()
                                .thenComparing(
                                        item -> degrees.getOrDefault(item.entity().id(), 0L),
                                        Comparator.reverseOrder())
                                .thenComparing(item -> item.entity().id()))
                        .map(item -> new RankedItem<>(
                                item.entity().id().toString(),
                                item,
                                seedScores.getOrDefault(item.entity().id(), 0.0)))
                        .toList();
        List<RankedItem<PermissionScopedGraphView.RelationView>> orderedRelations =
                rankIncidentRelations(request, relationViews.values(), degrees);
        return new Branch(orderedEntities, orderedRelations, seeds.size());
    }

    private Branch globalBranch(LightRagQueryRequest request, FloatVector highLevelVector) {
        if (highLevelVector == null) {
            return Branch.empty();
        }
        var search = vectorSearch(request, highLevelVector, request.options().topK());
        List<RankedItem<CanonicalRelation>> seeds =
                projection.searchRelations(request.scope(), request.snapshot(), search);
        if (seeds.isEmpty()) {
            return Branch.empty();
        }
        LinkedHashSet<UUID> relationIds = seeds.stream()
                .map(item -> item.value().id())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<UUID> entityIds = new LinkedHashSet<>();
        seeds.forEach(seed -> {
            entityIds.add(seed.value().sourceEntityId());
            entityIds.add(seed.value().targetEntityId());
        });
        PermissionScopedGraphView view = scopedView(request, entityIds, relationIds);
        Map<UUID, PermissionScopedGraphView.EntityView> entityViews =
                index(view.entities(), item -> item.entity().id());
        Map<UUID, PermissionScopedGraphView.RelationView> relationViews =
                index(view.relations(), item -> item.relation().id());
        Map<UUID, Double> seedScores = seeds.stream().collect(Collectors.toMap(
                item -> item.value().id(),
                RankedItem::score,
                Math::max,
                LinkedHashMap::new));
        List<RankedItem<PermissionScopedGraphView.RelationView>> orderedRelations =
                seeds.stream()
                        .map(seed -> relationViews.get(seed.value().id()))
                        .filter(Objects::nonNull)
                        .map(item -> new RankedItem<>(
                                item.relation().id().toString(),
                                item,
                                seedScores.get(item.relation().id())))
                        .toList();
        LinkedHashMap<UUID, PermissionScopedGraphView.EntityView> orderedEntityMap =
                new LinkedHashMap<>();
        for (RankedItem<PermissionScopedGraphView.RelationView> relation : orderedRelations) {
            orderedEntityMap.putIfAbsent(
                    relation.value().relation().sourceEntityId(),
                    entityViews.get(relation.value().relation().sourceEntityId()));
            orderedEntityMap.putIfAbsent(
                    relation.value().relation().targetEntityId(),
                    entityViews.get(relation.value().relation().targetEntityId()));
        }
        orderedEntityMap.values().removeIf(Objects::isNull);
        List<RankedItem<PermissionScopedGraphView.EntityView>> orderedEntities =
                orderedEntityMap.values().stream()
                        .map(item -> new RankedItem<>(
                                item.entity().id().toString(), item, 0.0))
                        .toList();
        return new Branch(orderedEntities, orderedRelations, seeds.size());
    }

    private PermissionScopedGraphView scopedView(
            LightRagQueryRequest request,
            Collection<UUID> entityIds,
            Collection<UUID> relationIds) {
        List<EntityContribution> entities = projection.loadEntityContributions(
                request.scope(), request.snapshot(), entityIds);
        List<RelationContribution> relations = projection.loadRelationContributions(
                request.scope(), request.snapshot(), relationIds);
        return PermissionScopedGraphMerger.merge(request.scope(), entities, relations);
    }

    private List<RankedItem<PermissionScopedGraphView.RelationView>> rankIncidentRelations(
            LightRagQueryRequest request,
            Collection<PermissionScopedGraphView.RelationView> relations,
            Map<UUID, Long> entityDegrees) {
        List<UUID> relationIds = relations.stream()
                .map(item -> item.relation().id())
                .toList();
        Map<UUID, Double> weights = projection.loadVisibleRelationWeights(
                request.scope(), request.snapshot(), relationIds);
        return relations.stream()
                .sorted(Comparator
                        .comparingLong((PermissionScopedGraphView.RelationView item) ->
                                entityDegrees.getOrDefault(
                                                item.relation().sourceEntityId(), 0L)
                                        + entityDegrees.getOrDefault(
                                                item.relation().targetEntityId(), 0L))
                        .reversed()
                        .thenComparing(
                                item -> weights.getOrDefault(item.relation().id(), 0.0),
                                Comparator.reverseOrder())
                        .thenComparing(item -> item.relation().id()))
                .map(item -> new RankedItem<>(
                        item.relation().id().toString(),
                        item,
                        weights.getOrDefault(item.relation().id(), 0.0)))
                .toList();
    }

    private List<ChunkState> supportChunks(
            LightRagQueryRequest request,
            List<?> views,
            LightRagQueryResult.Origin origin,
            Set<UUID> excluded,
            FloatVector queryVector) {
        List<List<UUID>> groups = new ArrayList<>();
        for (Object view : views) {
            List<UUID> ids = evidenceChunkIds(view).stream()
                    .filter(id -> !excluded.contains(id))
                    .distinct()
                    .toList();
            if (!ids.isEmpty()) {
                groups.add(ids);
            }
        }
        if (groups.isEmpty()) {
            return List.of();
        }
        Map<UUID, Integer> frequency = new LinkedHashMap<>();
        groups.forEach(group -> group.forEach(id -> frequency.merge(id, 1, Integer::sum)));
        Set<UUID> claimed = new HashSet<>();
        List<List<UUID>> deduplicated = groups.stream()
                .map(group -> group.stream()
                        .filter(claimed::add)
                        .sorted(Comparator
                                .comparingInt((UUID id) -> frequency.getOrDefault(id, 0))
                                .reversed()
                                .thenComparing(Function.identity()))
                        .toList())
                .filter(group -> !group.isEmpty())
                .toList();
        List<RankedItem<AuthorizedQueryProjection.Chunk>> ranked = List.of();
        if (request.options().relatedChunkSelection()
                        == LightRagQueryRequest.RelatedChunkSelection.VECTOR
                && queryVector != null) {
            int limit = Math.max(1,
                    request.options().relatedChunkNumber() * deduplicated.size() / 2);
            try {
                ranked = projection.rankChunks(
                        request.scope(),
                        request.snapshot(),
                        vectorSearch(request, queryVector, limit),
                        deduplicated.stream().flatMap(Collection::stream).toList());
            } catch (RuntimeException providerFailure) {
                // Provider diagnostics belong to the imperative shell. Core preserves
                // authorized ordering by falling back to weighted polling.
                ranked = List.of();
            }
        }
        List<UUID> selectedIds;
        Map<UUID, Double> scores = new HashMap<>();
        if (!ranked.isEmpty()) {
            selectedIds = ranked.stream().map(item -> item.value().id()).toList();
            ranked.forEach(item -> scores.put(item.value().id(), item.score()));
        } else {
            selectedIds = weightedPolling(
                    deduplicated, request.options().relatedChunkNumber(), 1);
        }
        Map<UUID, AuthorizedQueryProjection.Chunk> chunks = index(
                projection.loadChunks(request.scope(), request.snapshot(), selectedIds),
                AuthorizedQueryProjection.Chunk::id);
        List<ChunkState> result = new ArrayList<>();
        int order = 1;
        for (UUID id : selectedIds) {
            AuthorizedQueryProjection.Chunk chunk = chunks.get(id);
            if (chunk != null) {
                result.add(new ChunkState(
                        chunk,
                        origin,
                        frequency.getOrDefault(id, 1),
                        order++,
                        scores.getOrDefault(id, 0.0),
                        null));
            }
        }
        return List.copyOf(result);
    }

    private List<ChunkState> vectorChunks(
            LightRagQueryRequest request, FloatVector queryVector) {
        if (queryVector == null) {
            return List.of();
        }
        List<RankedItem<AuthorizedQueryProjection.Chunk>> hits =
                projection.searchChunks(
                        request.scope(),
                        request.snapshot(),
                        vectorSearch(request, queryVector, request.options().chunkTopK()));
        List<ChunkState> result = new ArrayList<>();
        int order = 1;
        for (RankedItem<AuthorizedQueryProjection.Chunk> hit : hits) {
            result.add(new ChunkState(
                    hit.value(),
                    LightRagQueryResult.Origin.VECTOR,
                    1,
                    order++,
                    hit.score(),
                    null));
        }
        return List.copyOf(result);
    }

    private RerankOutcome rerank(
            LightRagQueryRequest request, List<ChunkState> chunks) {
        if (chunks.isEmpty() || !request.options().rerankEnabled()) {
            return new RerankOutcome(
                    chunks.stream().limit(request.options().chunkTopK()).toList(),
                    false,
                    false,
                    Duration.ZERO);
        }
        long startedAt = System.nanoTime();
        try {
            List<ChunkReranker.Score> scores = reranker.rerank(
                    request.query(),
                    chunks.stream()
                            .map(item -> new ChunkReranker.Candidate(
                                    item.chunk().id(), item.chunk().content()))
                            .toList(),
                    request.options().chunkTopK());
            if (scores.isEmpty()) {
                return new RerankOutcome(
                        chunks.stream().limit(request.options().chunkTopK()).toList(),
                        true,
                        true,
                        elapsed(startedAt));
            }
            Map<UUID, Double> byChunk = scores.stream().collect(Collectors.toMap(
                    ChunkReranker.Score::chunkId,
                    ChunkReranker.Score::relevance,
                    Math::max));
            List<ChunkState> reranked = chunks.stream()
                    .map(item -> item.withRerank(byChunk.get(item.chunk().id())))
                    .filter(item -> item.rerankScore() != null)
                    .filter(item -> item.rerankScore()
                            >= request.options().minimumRerankScore())
                    .sorted(Comparator
                            .comparingDouble((ChunkState item) -> item.rerankScore())
                            .reversed()
                            .thenComparingInt(ChunkState::order)
                            .thenComparing(item -> item.chunk().id()))
                    .limit(request.options().chunkTopK())
                    .toList();
            return new RerankOutcome(
                    reranked,
                    true,
                    false,
                    elapsed(startedAt));
        } catch (RuntimeException providerFailure) {
            // The immutable trace records this fail-open path; the delivery shell
            // records provider diagnostics without exposing them to the answer.
            return new RerankOutcome(
                    chunks.stream().limit(request.options().chunkTopK()).toList(),
                    true,
                    true,
                    elapsed(startedAt));
        }
    }

    private LightRagQueryResult assemble(
            LightRagQueryRequest request,
            KeywordPlan keywords,
            List<String> embeddingInputs,
            int entitySeedCount,
            int relationSeedCount,
            int vectorChunkCount,
            List<RankedItem<PermissionScopedGraphView.EntityView>> rankedEntities,
            List<RankedItem<PermissionScopedGraphView.RelationView>> rankedRelations,
            RerankOutcome reranked) {
        var prepared = groundingAssembler.prepare(
                request.query(),
                request.options(),
                selectedEntities(rankedEntities),
                selectedRelations(rankedRelations),
                selectedChunks(reranked.chunks()),
                List.of(new LightRagGrounding.ScopeSnapshot(
                        request.snapshot().namespace(),
                        request.snapshot().batchId(),
                        request.snapshot().generation(),
                        request.snapshot().manifestFingerprint(),
                        request.scope().authorizationFingerprint())));
        if (prepared.grounding().empty()) {
            return noResults(request, keywords, embeddingInputs, "no_authorized_context");
        }
        List<LightRagQueryResult.ChunkSignal> signals =
                prepared.grounding().chunks().stream()
                .map(LightRagQueryEngine::signal)
                .toList();
        LightRagQueryResult.Trace trace = trace(
                request,
                keywords,
                embeddingInputs,
                entitySeedCount,
                relationSeedCount,
                vectorChunkCount,
                prepared.grounding().entities().size(),
                prepared.grounding().relations().size(),
                prepared.grounding().chunks().size(),
                reranked.attempted(),
                reranked.fallback(),
                reranked.duration(),
                signals,
                "");
        LightRagQueryResult.Answer answer = switch (request.options().outputMode()) {
            case CONTEXT, PROMPT -> new LightRagQueryResult.NoAnswer();
            case ANSWER -> answer(answerModel.answer(new QueryAnswerModel.Request(
                    request.query(),
                    prepared.systemPrompt(),
                    request.conversationHistory(),
                    request.options().streaming())));
        };
        return new LightRagQueryResult(
                LightRagQueryResult.Status.SUCCESS,
                prepared.context(),
                request.options().outputMode() == QueryOutputMode.CONTEXT
                        ? ""
                        : prepared.prompt(),
                answer,
                prepared.references(),
                prepared.grounding(),
                trace);
    }

    private LightRagQueryResult noResults(
            LightRagQueryRequest request,
            KeywordPlan keywords,
            List<String> embeddingInputs,
            String reason) {
        return new LightRagQueryResult(
                LightRagQueryResult.Status.NO_RESULTS,
                "",
                "",
                new LightRagQueryResult.NoAnswer(),
                List.of(),
                trace(
                        request,
                        keywords,
                        embeddingInputs,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        false,
                        false,
                        Duration.ZERO,
                        List.of(),
                        reason));
    }

    private LightRagQueryResult.Trace trace(
            LightRagQueryRequest request,
            KeywordPlan keywords,
            List<String> embeddingInputs,
            int entitySeedCount,
            int relationSeedCount,
            int vectorChunkCount,
            int selectedEntityCount,
            int selectedRelationCount,
            int selectedChunkCount,
            boolean rerankAttempted,
            boolean rerankFallback,
            Duration rerankDuration,
            List<LightRagQueryResult.ChunkSignal> signals,
            String failureReason) {
        return new LightRagQueryResult.Trace(
                request.options().mode(),
                keywords,
                embeddingInputs,
                entitySeedCount,
                relationSeedCount,
                vectorChunkCount,
                selectedEntityCount,
                selectedRelationCount,
                selectedChunkCount,
                rerankAttempted,
                rerankFallback,
                rerankDuration,
                signals,
                request.scope().authorizationFingerprint(),
                request.snapshot().generation(),
                failureReason);
    }

    private static List<UUID> evidenceChunkIds(Object view) {
        if (view instanceof PermissionScopedGraphView.EntityView entity) {
            return entity.evidence().stream()
                    .filter(reference -> reference.chunkId() != null)
                    .map(reference -> reference.chunkId())
                    .toList();
        }
        if (view instanceof PermissionScopedGraphView.RelationView relation) {
            return relation.evidence().stream()
                    .filter(reference -> reference.chunkId() != null)
                    .map(reference -> reference.chunkId())
                    .toList();
        }
        throw new IllegalArgumentException("unsupported graph view type");
    }

    static List<UUID> weightedPolling(
            List<List<UUID>> groups, int maximum, int minimum) {
        if (groups.isEmpty()) {
            return List.of();
        }
        if (groups.size() == 1) {
            return groups.getFirst().stream().limit(maximum).toList();
        }
        int count = groups.size();
        List<Integer> expected = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double ratio = (double) index / (count - 1);
            expected.add((int) Math.round(maximum - ratio * (maximum - minimum)));
        }
        List<UUID> selected = new ArrayList<>();
        int[] used = new int[count];
        int remaining = 0;
        for (int index = 0; index < count; index++) {
            int actual = Math.min(expected.get(index), groups.get(index).size());
            selected.addAll(groups.get(index).subList(0, actual));
            used[index] = actual;
            remaining += expected.get(index) - actual;
        }
        for (int allocation = 0; allocation < remaining; allocation++) {
            boolean added = false;
            for (int index = 0; index < count; index++) {
                if (used[index] < groups.get(index).size()) {
                    selected.add(groups.get(index).get(used[index]++));
                    added = true;
                    break;
                }
            }
            if (!added) {
                break;
            }
        }
        return List.copyOf(selected);
    }

    private static <T> List<RankedItem<T>> mergeRanked(
            List<RankedItem<T>> first,
            List<RankedItem<T>> second,
            int limit) {
        return RoundRobinInterleaver.merge(List.of(first, second), limit);
    }

    private static List<ChunkState> interleaveChunks(
            List<ChunkState> vector,
            List<ChunkState> entity,
            List<ChunkState> relation) {
        int maximum = Math.max(vector.size(), Math.max(entity.size(), relation.size()));
        Set<UUID> seen = new LinkedHashSet<>();
        List<ChunkState> result = new ArrayList<>();
        for (int offset = 0; offset < maximum; offset++) {
            appendAt(vector, offset, seen, result);
            appendAt(entity, offset, seen, result);
            appendAt(relation, offset, seen, result);
        }
        return List.copyOf(result);
    }

    private static void appendAt(
            List<ChunkState> source,
            int offset,
            Set<UUID> seen,
            List<ChunkState> target) {
        if (offset < source.size() && seen.add(source.get(offset).chunk().id())) {
            target.add(source.get(offset));
        }
    }

    private static List<LightRagGrounding.SelectedEntity> selectedEntities(
            List<RankedItem<PermissionScopedGraphView.EntityView>> ranked) {
        List<LightRagGrounding.SelectedEntity> selected =
                new ArrayList<>(ranked.size());
        int order = 1;
        for (RankedItem<PermissionScopedGraphView.EntityView> item : ranked) {
            var value = item.value();
            selected.add(new LightRagGrounding.SelectedEntity(
                    value.entity().id(),
                    value.entity().normalizedName(),
                    LightRagGrounding.entityContributions(value),
                    item.score(),
                    order++));
        }
        return List.copyOf(selected);
    }

    private static List<LightRagGrounding.SelectedRelation> selectedRelations(
            List<RankedItem<PermissionScopedGraphView.RelationView>> ranked) {
        List<LightRagGrounding.SelectedRelation> selected =
                new ArrayList<>(ranked.size());
        int order = 1;
        for (RankedItem<PermissionScopedGraphView.RelationView> item : ranked) {
            var value = item.value();
            selected.add(new LightRagGrounding.SelectedRelation(
                    value.relation().id(),
                    value.relation().sourceEntityId(),
                    value.relation().targetEntityId(),
                    value.sourceEntityName(),
                    value.targetEntityName(),
                    LightRagGrounding.relationContributions(value),
                    item.score(),
                    order++));
        }
        return List.copyOf(selected);
    }

    private static List<LightRagGrounding.SelectedChunk> selectedChunks(
            List<ChunkState> chunks) {
        return chunks.stream()
                .map(chunk -> new LightRagGrounding.SelectedChunk(
                        chunk.chunk().id(),
                        chunk.chunk().evidence(),
                        chunk.chunk().projectionGeneration(),
                        chunk.chunk().content(),
                        chunk.chunk().metadata(),
                        chunk.origin(),
                        chunk.frequency(),
                        chunk.order(),
                        chunk.retrievalScore(),
                        chunk.rerankScore()))
                .toList();
    }

    private static LightRagQueryResult.ChunkSignal signal(
            LightRagGrounding.SelectedChunk chunk) {
        return new LightRagQueryResult.ChunkSignal(
                chunk.id(),
                chunk.origin(),
                chunk.frequency(),
                chunk.order(),
                chunk.retrievalScore(),
                chunk.rerankScore());
    }

    private static String optionalInstruction(String instruction) {
        return instruction == null || instruction.isBlank() ? "n/a" : instruction.strip();
    }

    private static LightRagQueryResult.Answer answer(QueryAnswerModel.Response response) {
        return switch (Objects.requireNonNull(response, "response")) {
            case QueryAnswerModel.Complete complete ->
                    new LightRagQueryResult.CompleteAnswer(complete.content());
            case QueryAnswerModel.Streaming streaming ->
                    new LightRagQueryResult.StreamingAnswer(streaming.chunks());
        };
    }

    private AuthorizedQueryProjection.VectorSearch vectorSearch(
            LightRagQueryRequest request, FloatVector vector, int limit) {
        return new AuthorizedQueryProjection.VectorSearch(
                request.embeddingProfileId(),
                request.embeddingDimensions(),
                vector,
                limit,
                request.options().minimumVectorSimilarity());
    }

    private static FloatVector vector(
            Map<Purpose, Integer> offsets,
            List<FloatVector> vectors,
            Purpose purpose) {
        Integer offset = offsets.get(purpose);
        return offset == null ? null : vectors.get(offset);
    }

    private static <K, V> Map<K, V> index(
            Collection<V> values, Function<V, K> key) {
        return values.stream().collect(Collectors.toMap(
                key,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    private enum Purpose {
        QUERY,
        LOW_LEVEL,
        HIGH_LEVEL
    }

    private record EmbeddingPlan(
            List<String> inputs,
            FloatVector query,
            FloatVector lowLevel,
            FloatVector highLevel) {

        private EmbeddingPlan {
            inputs = List.copyOf(inputs);
        }
    }

    private record Branch(
            List<RankedItem<PermissionScopedGraphView.EntityView>> entities,
            List<RankedItem<PermissionScopedGraphView.RelationView>> relations,
            int seedCount) {

        private Branch {
            entities = List.copyOf(entities);
            relations = List.copyOf(relations);
            if (seedCount < 0) {
                throw new IllegalArgumentException("seedCount must be non-negative");
            }
        }

        private static Branch empty() {
            return new Branch(List.of(), List.of(), 0);
        }
    }

    private record ChunkState(
            AuthorizedQueryProjection.Chunk chunk,
            LightRagQueryResult.Origin origin,
            int frequency,
            int order,
            double retrievalScore,
            Double rerankScore) {

        private ChunkState {
            Objects.requireNonNull(chunk, "chunk");
            Objects.requireNonNull(origin, "origin");
            if (frequency <= 0 || order <= 0 || !Double.isFinite(retrievalScore)) {
                throw new IllegalArgumentException("chunk state values must be valid");
            }
        }

        private ChunkState withRerank(Double score) {
            return new ChunkState(
                    chunk, origin, frequency, order, retrievalScore, score);
        }

    }

    private record RerankOutcome(
            List<ChunkState> chunks,
            boolean attempted,
            boolean fallback,
            Duration duration) {

        private RerankOutcome {
            chunks = List.copyOf(chunks);
            Objects.requireNonNull(duration, "duration");
            if (duration.isNegative()) {
                throw new IllegalArgumentException(
                        "rerank duration must not be negative");
            }
            if (fallback && !attempted) {
                throw new IllegalArgumentException(
                        "rerank fallback requires an attempted rerank");
            }
        }
    }

    private static Duration elapsed(long startedAt) {
        return Duration.ofNanos(Math.max(
                0,
                System.nanoTime() - startedAt));
    }
}
