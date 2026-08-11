package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.ai.ChatGenerationRequest;
import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.asset.KnowledgeProjectionNamespaces;
import com.orgmemory.core.knowledge.search.KnowledgeEvidenceSelection;
import com.orgmemory.core.knowledge.search.RetrievedKnowledgeEvidence;
import com.orgmemory.core.knowledge.search.SecureKnowledgeSearchResult;
import com.orgmemory.core.knowledge.search.VerifiedKnowledgeGrounding;
import com.orgmemory.core.knowledge.sourceledger.SourceCitationUri;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import com.orgmemory.graphrag.cache.CanonicalCacheKeyHasher;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.observability.GraphRagEventSink;
import com.orgmemory.graphrag.observability.GraphRagTaskDecorator;
import com.orgmemory.graphrag.query.ContextTokenUsage;
import com.orgmemory.graphrag.query.KeywordPlan;
import com.orgmemory.graphrag.query.LightRagGrounding;
import com.orgmemory.graphrag.query.LightRagGroundingAssembler;
import com.orgmemory.graphrag.query.LightRagPreparedQuery;
import com.orgmemory.graphrag.query.LightRagQueryEngine;
import com.orgmemory.graphrag.query.LightRagQueryMode;
import com.orgmemory.graphrag.query.LightRagQueryRequest;
import com.orgmemory.graphrag.query.LightRagQueryResult;
import com.orgmemory.graphrag.query.QueryOutputMode;
import com.orgmemory.graphrag.query.SecureContextBudget;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Permission-aware application shell around the framework-neutral LightRAG
 * engine. OpenFGA ListObjects establishes the scope; BatchCheck and the
 * canonical ledger recheck it before any selected evidence reaches the model.
 */
class DefaultGraphRagKnowledgeRetrievalService
        implements GraphRagKnowledgeRetrievalService {

    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final String RESOURCE_TYPE = "knowledge_asset";
    private static final OpenFgaBatchRecheck.ReasonRule RESULT_REASON =
            OpenFgaBatchRecheck.ReasonRule.resultReason();
    private static final OpenFgaBatchRecheck.ReasonRule FINAL_RECHECK_DENIED =
            OpenFgaBatchRecheck.ReasonRule.fixed(
                    "FINAL_OPENFGA_RECHECK_DENIED");
    private static final OpenFgaBatchRecheck.ReasonMapping RECHECK_REASONS =
            new OpenFgaBatchRecheck.ReasonMapping(
                    RESULT_REASON,
                    RESULT_REASON,
                    RESULT_REASON,
                    FINAL_RECHECK_DENIED,
                    FINAL_RECHECK_DENIED,
                    FINAL_RECHECK_DENIED);
    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final int RECALL_TOP_K = 40;
    private static final int DIAGNOSTIC_TOP_K = 60;

    private final KnowledgeSearchAuthorizationService searchAuthorization;
    private final KnowledgeEvidenceScopeResolver evidenceScopes;
    private final OpenFgaBatchRecheck batchRecheck;
    private final SecureKnowledgeRetrievalStore canonicalEvidence;
    private final EmbeddingProfileRegistry embeddingProfiles;
    private final KnowledgeEmbeddingProperties embedding;
    private final ProjectionPublicationStore publications;
    private final LightRagQueryEngine engine;
    private final GraphRagRetrievalPolicy policy;
    private final PermissionAuditService audit;
    private final KnowledgeRetrievalProperties retrievalProperties;
    private final GraphRagEventSink events;
    private final GraphRagTaskDecorator tasks;
    private final RetrievalAdmissionControl admission;

    DefaultGraphRagKnowledgeRetrievalService(
            KnowledgeSearchAuthorizationService searchAuthorization,
            KnowledgeEvidenceScopeResolver evidenceScopes,
            RelationshipAuthorizationSetPort authorization,
            SecureKnowledgeRetrievalStore canonicalEvidence,
            EmbeddingProfileRegistry embeddingProfiles,
            KnowledgeEmbeddingProperties embedding,
            ProjectionPublicationStore publications,
            LightRagQueryEngine engine,
            GraphRagRetrievalPolicy policy,
            PermissionAuditService audit,
            KnowledgeRetrievalProperties retrievalProperties,
            GraphRagEventSink events) {
        this(
                searchAuthorization,
                evidenceScopes,
                authorization,
                canonicalEvidence,
                embeddingProfiles,
                embedding,
                publications,
                engine,
                policy,
                audit,
                retrievalProperties,
                events,
                GraphRagTaskDecorator.NONE,
                new RetrievalAdmissionControl(
                        policy.retrievalAdmissionPermits()));
    }

    DefaultGraphRagKnowledgeRetrievalService(
            KnowledgeSearchAuthorizationService searchAuthorization,
            KnowledgeEvidenceScopeResolver evidenceScopes,
            RelationshipAuthorizationSetPort authorization,
            SecureKnowledgeRetrievalStore canonicalEvidence,
            EmbeddingProfileRegistry embeddingProfiles,
            KnowledgeEmbeddingProperties embedding,
            ProjectionPublicationStore publications,
            LightRagQueryEngine engine,
            GraphRagRetrievalPolicy policy,
            PermissionAuditService audit,
            KnowledgeRetrievalProperties retrievalProperties,
            GraphRagEventSink events,
            GraphRagTaskDecorator tasks) {
        this(
                searchAuthorization,
                evidenceScopes,
                authorization,
                canonicalEvidence,
                embeddingProfiles,
                embedding,
                publications,
                engine,
                policy,
                audit,
                retrievalProperties,
                events,
                tasks,
                new RetrievalAdmissionControl(
                        policy.retrievalAdmissionPermits()));
    }

    DefaultGraphRagKnowledgeRetrievalService(
            KnowledgeSearchAuthorizationService searchAuthorization,
            KnowledgeEvidenceScopeResolver evidenceScopes,
            RelationshipAuthorizationSetPort authorization,
            SecureKnowledgeRetrievalStore canonicalEvidence,
            EmbeddingProfileRegistry embeddingProfiles,
            KnowledgeEmbeddingProperties embedding,
            ProjectionPublicationStore publications,
            LightRagQueryEngine engine,
            GraphRagRetrievalPolicy policy,
            PermissionAuditService audit,
            KnowledgeRetrievalProperties retrievalProperties,
            GraphRagEventSink events,
            GraphRagTaskDecorator tasks,
            RetrievalAdmissionControl admission) {
        this.searchAuthorization = searchAuthorization;
        this.evidenceScopes = evidenceScopes;
        this.batchRecheck = new OpenFgaBatchRecheck(authorization);
        this.canonicalEvidence = canonicalEvidence;
        this.embeddingProfiles = embeddingProfiles;
        this.embedding = embedding;
        this.publications = publications;
        this.engine = engine;
        this.policy = policy;
        this.audit = audit;
        this.retrievalProperties = retrievalProperties;
        this.events = Objects.requireNonNull(events, "events");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.admission = Objects.requireNonNull(admission, "admission");
    }

    @Override
    public SecureKnowledgeSearchResult search(
            CurrentActor actor,
            String query,
            Integer requestedLimit,
            String suppliedRequestId) {
        String normalizedQuery = normalizeQuery(query);
        int limit = validateLimit(requestedLimit);
        return runRetrieval(
                        actor,
                        normalizedQuery,
                        requestId(suppliedRequestId),
                        policy.contextOptions(limit),
                        KnowledgeEvidenceSelection.unrestricted())
                .result();
    }

    @Override
    public SecureKnowledgeSearchResult search(
            CurrentActor actor,
            String query,
            Integer requestedLimit,
            String suppliedRequestId,
            KnowledgeEvidenceSelection selection) {
        String normalizedQuery = normalizeQuery(query);
        int limit = validateLimit(requestedLimit);
        return runRetrieval(
                        actor,
                        normalizedQuery,
                        requestId(suppliedRequestId),
                        policy.contextOptions(limit),
                        Objects.requireNonNull(selection, "selection"))
                .result();
    }

    @Override
    public RetrievalObservation observe(
            CurrentActor actor,
            String query,
            String suppliedRequestId) {
        String normalizedQuery = normalizeQuery(query);
        String requestId = requestId(suppliedRequestId);
        RetrievalRun keywordSeeded = runRetrieval(
                actor,
                normalizedQuery,
                observationRequestId(requestId, "-keyword"),
                observationOptions(LightRagQueryMode.MIX, DIAGNOSTIC_TOP_K),
                KnowledgeEvidenceSelection.unrestricted());
        RetrievalRun bypass = runRetrieval(
                actor,
                normalizedQuery,
                observationRequestId(requestId, "-bypass"),
                observationOptions(LightRagQueryMode.NAIVE, RECALL_TOP_K),
                KnowledgeEvidenceSelection.unrestricted());
        KeywordPlan keywordPlan = keywordSeeded.keywords();
        return new RetrievalObservation(
                documents(keywordSeeded.result()),
                documents(bypass.result()),
                new KeywordPlanSnapshot(
                        keywordPlan.highLevel(),
                        keywordPlan.lowLevel(),
                        keywordPlan.source().cacheValue()));
    }

    private RetrievalRun runRetrieval(
            CurrentActor actor,
            String normalizedQuery,
            String requestId,
            LightRagQueryRequest.Options options,
            KnowledgeEvidenceSelection selection) {
        Objects.requireNonNull(actor, "actor");
        UUID operationId = UUID.randomUUID();
        long startedAt = System.nanoTime();
        try {
            long authorizationStartedAt = System.nanoTime();
            String authorizationModelId = searchAuthorization.require(
                    actor,
                    requestId,
                    normalizedQuery);
            emitStage(
                    operationId,
                    actor.organizationId(),
                    GraphRagEventSink.Stage.AUTHORIZE,
                    authorizationStartedAt,
                    1,
                    1);
            RetrievalRun result = search(
                    actor,
                    normalizedQuery,
                    options,
                    requestId,
                    authorizationModelId,
                    operationId,
                    0,
                    selection);
            emit(
                    operationId,
                    actor.organizationId(),
                    GraphRagEventSink.Outcome.SUCCEEDED,
                    startedAt,
                    result.result().evidence().size(),
                    null);
            return result;
        } catch (RuntimeException failure) {
            emit(
                    operationId,
                    actor.organizationId(),
                    GraphRagEventSink.Outcome.FAILED,
                    startedAt,
                    0,
                    failureCode(failure));
            throw failure;
        }
    }

    private LightRagQueryRequest.Options observationOptions(
            LightRagQueryMode mode,
            int topK) {
        return new LightRagQueryRequest.Options(
                mode,
                QueryOutputMode.CONTEXT,
                "Multiple Paragraphs",
                "",
                topK,
                topK,
                policy.relatedChunkNumber(),
                policy.maximumGraphDepth(),
                LightRagQueryRequest.RelatedChunkSelection.VECTOR,
                policy.contextBudget(),
                policy.rerank().enabled(),
                policy.rerank().minimumScore(),
                policy.minimumVectorSimilarity(),
                policy.includeHeadings(),
                false);
    }

    private static String observationRequestId(
            String requestId,
            String suffix) {
        int prefixLength = Math.min(
                requestId.length(),
                MAX_REQUEST_ID_LENGTH - suffix.length());
        return requestId.substring(0, prefixLength) + suffix;
    }

    private static List<RetrievedDocument> documents(
            SecureKnowledgeSearchResult result) {
        Map<UUID, RetrievedDocument> documents = new LinkedHashMap<>();
        for (RetrievedKnowledgeEvidence evidence : result.evidence()) {
            documents.putIfAbsent(
                    evidence.sourceObjectId(),
                    new RetrievedDocument(
                            evidence.knowledgeAssetId(),
                            evidence.sourceObjectId(),
                            evidence.title()));
        }
        return List.copyOf(documents.values());
    }

    private void emit(
            UUID operationId,
            UUID organizationId,
            GraphRagEventSink.Outcome outcome,
            long startedAt,
            int outputCount,
            String failureCode) {
        safeEmit(new GraphRagEventSink.GraphRagEvent(
                    operationId,
                    organizationId,
                    GraphRagEventSink.Stage.RETRIEVE,
                    outcome,
                    Duration.ofNanos(Math.max(0, System.nanoTime() - startedAt)),
                    1,
                    outputCount,
                    null,
                    null,
                    null,
                    failureCode,
                    Instant.now()));
    }

    private static String failureCode(RuntimeException failure) {
        if (failure instanceof BusinessException business
                && business.category() == BusinessErrorCategory.VALIDATION) {
            return "invalid_request";
        }
        if (failure instanceof KnowledgeRetrievalUnavailableException) {
            return "retrieval_unavailable";
        }
        return "retrieval_failed";
    }

    private RetrievalRun search(
            CurrentActor actor,
            String query,
            LightRagQueryRequest.Options queryOptions,
            String requestId,
            String authorizationModelId,
            UUID operationId,
            int attempt,
            KnowledgeEvidenceSelection selection) {
        ResolvedKnowledgeEvidenceScope resolved =
                resolve(
                        actor,
                        authorizationModelId,
                        requestId,
                        query,
                        operationId);
        ResolvedKnowledgeEvidenceScope initial = restrict(resolved, selection);
        if (initial.allAssetIds().isEmpty()) {
            boolean selectionRemovedAuthorizedScope =
                    selection.restricted() && !resolved.allAssetIds().isEmpty();
            audit.record(searchAuthorization.command(
                    actor,
                    requestId,
                    query,
                    PermissionAuditDecision.ALLOW,
                    selectionRemovedAuthorizedScope
                            ? KnowledgeSearchAuthorizationService.NO_AUTHORIZED_SELECTED_KNOWLEDGE_ASSETS
                            : "NO_AUTHORIZED_KNOWLEDGE_ASSETS",
                    initial.authorizationModelId()));
            return RetrievalRun.empty(requestId);
        }

        EmbeddingProfileRef profile = embeddingProfiles
                .find(
                        actor.organizationId(),
                        new EmbeddingProfileSpec(
                                embedding.provider(),
                                embedding.model(),
                                embedding.dimensions(),
                                EmbeddingDistanceMetric.COSINE))
                .orElseThrow(() -> searchAuthorization.unavailable(
                        actor,
                        requestId,
                        query,
                        "EMBEDDING_PROFILE_NOT_INDEXED",
                        initial.authorizationModelId()));

        PublishedSpaceQuery published =
                queryPublishedSpaces(
                        initial,
                        profile,
                        query,
                        queryOptions,
                        operationId);
        List<LightRagGrounding> spaceGroundings = published.groundings();
        if (spaceGroundings.isEmpty()) {
            audit.record(searchAuthorization.command(
                    actor,
                    requestId,
                    query,
                    PermissionAuditDecision.ALLOW,
                    "NO_ELIGIBLE_EVIDENCE",
                    initial.authorizationModelId()));
            return RetrievalRun.empty(requestId, published.keywords());
        }
        long consolidationStartedAt = System.nanoTime();
        LightRagGroundingAssembler.PreparedGrounding consolidated =
                engine.consolidateGrounding(
                        query,
                        queryOptions,
                        spaceGroundings);
        emitAssembledContext(
                operationId,
                actor.organizationId(),
                consolidationStartedAt,
                spaceGroundings.size(),
                consolidated,
                queryOptions.contextBudget());
        if (consolidated.grounding().empty()
                || consolidated.grounding().chunks().isEmpty()) {
            audit.record(searchAuthorization.command(
                    actor,
                    requestId,
                    query,
                    PermissionAuditDecision.ALLOW,
                    "NO_CITABLE_GROUNDING",
                    initial.authorizationModelId()));
            return RetrievalRun.empty(requestId, published.keywords());
        }

        ResolvedKnowledgeEvidenceScope current =
                restrict(
                        resolve(
                        actor,
                        authorizationModelId,
                        requestId,
                        query,
                        operationId),
                        selection);
        if (!sameAuthorizationScope(initial, current)) {
            return retryOrFail(
                    actor,
                    query,
                    queryOptions,
                    requestId,
                    authorizationModelId,
                    operationId,
                    attempt,
                    selection,
                    "AUTHORIZATION_SCOPE_CHANGED");
        }

        List<LightRagGrounding.GroundingEvidence> closure =
                consolidated.grounding().evidenceClosure();
        if (closure.size() > policy.maximumEvidenceClosure()) {
            throw searchAuthorization.unavailable(
                    actor,
                    requestId,
                    query,
                    "GROUNDING_EVIDENCE_CLOSURE_EXCEEDED",
                    current.authorizationModelId());
        }
        long finalAuthorizationStartedAt = System.nanoTime();
        verifyOpenFga(
                actor,
                query,
                requestId,
                current.authorizationModelId(),
                closure);
        emitStage(
                operationId,
                actor.organizationId(),
                GraphRagEventSink.Stage.AUTHORIZE,
                finalAuthorizationStartedAt,
                closure.size(),
                closure.size());
        List<SecureRetrievalCandidate> verified =
                recheckCanonical(current, closure);
        if (!sameEvidence(closure, verified)) {
            return retryOrFail(
                    actor,
                    query,
                    queryOptions,
                    requestId,
                    authorizationModelId,
                    operationId,
                    attempt,
                    selection,
                    "CANONICAL_EVIDENCE_CHANGED");
        }
        if (verified.stream().anyMatch(candidate -> !selection.contains(
                candidate.knowledgeAssetId(),
                candidate.sourceObjectId(),
                candidate.sourceRevisionId()))) {
            throw searchAuthorization.unavailable(
                    actor,
                    requestId,
                    query,
                    "RETRIEVAL_EVIDENCE_SELECTION_BOUNDARY_VIOLATION",
                    current.authorizationModelId());
        }
        LightRagGroundingAssembler.PreparedGrounding rendered =
                engine.renderGrounding(
                        query,
                        queryOptions,
                        consolidated.grounding());

        Map<UUID, SecureRetrievalCandidate> canonicalByChunk = verified.stream()
                .collect(Collectors.toMap(
                        SecureRetrievalCandidate::chunkId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<UUID, Double> scoreByChunk = consolidated.grounding()
                .chunks()
                .stream()
                .collect(Collectors.toMap(
                        LightRagGrounding.SelectedChunk::id,
                        LightRagGrounding.SelectedChunk::effectiveScore,
                        Math::max,
                        LinkedHashMap::new));
        List<RetrievedKnowledgeEvidence> evidence = rendered.references()
                .stream()
                .map(reference -> toEvidence(
                        Objects.requireNonNull(
                                canonicalByChunk.get(
                                        reference.evidence().chunkId()),
                                "verified citation evidence"),
                        scoreByChunk.getOrDefault(
                                reference.evidence().chunkId(),
                                0.0)))
                .toList();
        List<PermissionAuditCommand> auditCommands = new ArrayList<>();
        auditCommands.add(searchAuthorization.command(
                actor,
                requestId,
                query,
                PermissionAuditDecision.ALLOW,
                "SECURE_GRAPH_RAG_RETRIEVAL_APPLIED",
                current.authorizationModelId()));
        for (LightRagGrounding.GroundingEvidence groundingEvidence : closure) {
            SecureRetrievalCandidate canonical = canonicalByChunk.get(
                    groundingEvidence.evidence().chunkId());
            auditCommands.add(evidenceAudit(
                    actor,
                    requestId,
                    query,
                    canonical,
                    current.authorizationModelId(),
                    "VERIFIED_GRAPH_RAG_GROUNDING"));
        }
        audit.recordAll(auditCommands);
        return new RetrievalRun(
                new SecureKnowledgeSearchResult(
                        requestId,
                        evidence,
                        Optional.of(new VerifiedKnowledgeGrounding(
                                new ChatGenerationRequest(
                                        rendered.systemPrompt(),
                                        query),
                                evidence,
                                closure.size(),
                                rendered.inputTokens()))),
                published.keywords());
    }

    private PublishedSpaceQuery queryPublishedSpaces(
            ResolvedKnowledgeEvidenceScope scope,
            EmbeddingProfileRef profile,
            String query,
            LightRagQueryRequest.Options options,
            UUID operationId) {
        if (scope.knowledgeSpaceIds().size()
                > policy.maximumKnowledgeSpaces()) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Secure knowledge retrieval is temporarily unavailable");
        }
        List<LightRagQueryRequest> requests = new ArrayList<>();
        for (UUID knowledgeSpaceId :
                scope.knowledgeSpaceIds().stream().sorted().toList()) {
            var evidenceScope = scope.forKnowledgeSpace(knowledgeSpaceId);
            ProjectionNamespace namespace = namespace(
                    scope.organizationId(),
                    knowledgeSpaceId);
            var snapshot = publications.current(namespace);
            if (snapshot.isEmpty()) {
                continue;
            }
            requests.add(new LightRagQueryRequest(
                            evidenceScope,
                            snapshot.orElseThrow(),
                            query,
                            options,
                            profile.id(),
                            profile.dimensions(),
                            null,
                            List.of()));
        }
        if (requests.isEmpty()) {
            return PublishedSpaceQuery.empty();
        }
        if (requests.size() > 1 && policy.rerank().enabled()) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Secure knowledge retrieval is temporarily unavailable");
        }

        LightRagPreparedQuery prepared = engine.prepare(requests.getFirst());
        emitPreparedStage(
                operationId,
                scope.organizationId(),
                GraphRagEventSink.Stage.PREPARE_QUERY,
                prepared.keywordPlanningDuration(),
                1,
                prepared.keywords().highLevel().size()
                        + prepared.keywords().lowLevel().size(),
                prepared.keywordModelRouteFingerprint(),
                prepared.keywordCacheStatus());
        emitPreparedStage(
                operationId,
                scope.organizationId(),
                GraphRagEventSink.Stage.EMBED,
                prepared.embeddingDuration(),
                prepared.embeddingInputs().size(),
                prepared.embeddingInputs().size(),
                null,
                null);
        List<LightRagGrounding> groundings = new ArrayList<>();
        try (ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor()) {
            var completed = new ExecutorCompletionService<IndexedSnapshotQueryResult>(
                    executor);
            List<Future<IndexedSnapshotQueryResult>> futures =
                    new ArrayList<>(requests.size());
            for (int index = 0; index < requests.size(); index++) {
                int resultIndex = index;
                LightRagQueryRequest request = requests.get(index);
                futures.add(completed.submit(tasks.decorate(() ->
                        new IndexedSnapshotQueryResult(
                                resultIndex,
                                queryPublishedSpace(
                                        operationId,
                                        request,
                                        prepared)))));
            }
            SnapshotQueryResult[] ordered =
                    new SnapshotQueryResult[requests.size()];
            try {
                for (int completedCount = 0;
                        completedCount < requests.size();
                        completedCount++) {
                    IndexedSnapshotQueryResult result =
                            completed.take().get();
                    ordered[result.index()] = result.result();
                }
            } catch (ExecutionException | InterruptedException
                    | RuntimeException failure) {
                futures.forEach(future -> future.cancel(true));
                throw retrievalFailure(failure);
            }
            for (SnapshotQueryResult snapshotResult : ordered) {
                emitSnapshotStage(
                        operationId,
                        scope.organizationId(),
                        snapshotResult.duration(),
                        snapshotResult.inputCount(),
                        snapshotResult.result()
                                .grounding()
                                .chunks()
                                .size(),
                        snapshotResult.namespace());
                emitRerank(
                        operationId,
                        scope.organizationId(),
                        snapshotResult.result());
                LightRagGrounding grounding =
                        snapshotResult.result().grounding();
                if (!grounding.empty()) {
                    groundings.add(grounding);
                }
            }
        }
        return new PublishedSpaceQuery(groundings, prepared.keywords());
    }

    private SnapshotQueryResult queryPublishedSpace(
            UUID operationId,
            LightRagQueryRequest request,
            LightRagPreparedQuery prepared) throws Exception {
        long startedAt = System.nanoTime();
        LightRagQueryResult result = admission.execute(() -> engine.executePrepared(
                request,
                prepared,
                measurement -> emitQueryOperation(
                        operationId,
                        request.scope().organizationId(),
                        request.scope().authorizationFingerprint(),
                        measurement)));
        return new SnapshotQueryResult(
                result,
                Duration.ofNanos(Math.max(
                        0,
                        System.nanoTime() - startedAt)),
                request.scope().authorizedAssetIds().size(),
                request.snapshot().namespace());
    }

    private void emitQueryOperation(
            UUID operationId,
            UUID organizationId,
            String scopeFingerprint,
            LightRagQueryEngine.QueryOperationMeasurement measurement) {
        GraphRagEventSink.Outcome outcome = switch (measurement.outcome()) {
            case SUCCEEDED -> GraphRagEventSink.Outcome.SUCCEEDED;
            case FAILED -> GraphRagEventSink.Outcome.FAILED;
        };
        safeEmit(new GraphRagEventSink.GraphRagEvent(
                operationId,
                organizationId,
                queryOperationStage(measurement.operation()),
                outcome,
                measurement.duration(),
                measurement.inputCount(),
                measurement.outputCount(),
                null,
                scopeFingerprint,
                null,
                outcome == GraphRagEventSink.Outcome.FAILED
                        ? "query_operation_failed"
                        : null,
                Instant.now()));
    }

    private static GraphRagEventSink.Stage queryOperationStage(
            LightRagQueryEngine.QueryOperation operation) {
        return switch (operation) {
            case SEARCH_ENTITIES -> GraphRagEventSink.Stage.QUERY_SEARCH_ENTITIES;
            case SEARCH_RELATIONS -> GraphRagEventSink.Stage.QUERY_SEARCH_RELATIONS;
            case SEARCH_CHUNKS -> GraphRagEventSink.Stage.QUERY_SEARCH_CHUNKS;
            case EXPAND_ENTITY_IDS -> GraphRagEventSink.Stage.QUERY_EXPAND_ENTITY_IDS;
            case LOAD_INCIDENT_RELATIONS ->
                    GraphRagEventSink.Stage.QUERY_LOAD_INCIDENT_RELATIONS;
            case LOAD_ENTITY_CONTRIBUTIONS ->
                    GraphRagEventSink.Stage.QUERY_LOAD_ENTITY_CONTRIBUTIONS;
            case LOAD_RELATION_CONTRIBUTIONS ->
                    GraphRagEventSink.Stage.QUERY_LOAD_RELATION_CONTRIBUTIONS;
            case LOAD_VISIBLE_ENTITY_DEGREES ->
                    GraphRagEventSink.Stage.QUERY_LOAD_VISIBLE_ENTITY_DEGREES;
            case LOAD_VISIBLE_RELATION_WEIGHTS ->
                    GraphRagEventSink.Stage.QUERY_LOAD_VISIBLE_RELATION_WEIGHTS;
            case RANK_CHUNKS -> GraphRagEventSink.Stage.QUERY_RANK_CHUNKS;
            case LOAD_CHUNKS -> GraphRagEventSink.Stage.QUERY_LOAD_CHUNKS;
        };
    }

    private record IndexedSnapshotQueryResult(
            int index,
            SnapshotQueryResult result) {
    }

    private static RuntimeException retrievalFailure(Exception failure) {
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return new KnowledgeRetrievalUnavailableException(
                    "Secure knowledge retrieval is temporarily unavailable");
        }
        Throwable cause = failure instanceof ExecutionException
                ? failure.getCause()
                : failure;
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new KnowledgeRetrievalUnavailableException(
                "Secure knowledge retrieval is temporarily unavailable");
    }

    private ResolvedKnowledgeEvidenceScope resolve(
            CurrentActor actor,
            String authorizationModelId,
            String requestId,
            String query,
            UUID operationId) {
        long startedAt = System.nanoTime();
        try {
            ResolvedKnowledgeEvidenceScope resolved =
                    evidenceScopes.resolve(actor, authorizationModelId);
            emitStage(
                    operationId,
                    actor.organizationId(),
                    GraphRagEventSink.Stage.AUTHORIZE,
                    startedAt,
                    1,
                    resolved.allAssetIds().size());
            return resolved;
        } catch (KnowledgeEvidenceScopeUnavailableException unavailable) {
            throw searchAuthorization.unavailable(
                    actor,
                    requestId,
                    query,
                    unavailable.reasonCode(),
                    unavailable.policyVersion());
        }
    }

    private void emitStage(
            UUID operationId,
            UUID organizationId,
            GraphRagEventSink.Stage stage,
            long startedAt,
            int inputCount,
            int outputCount) {
        safeEmit(new GraphRagEventSink.GraphRagEvent(
                    operationId,
                    organizationId,
                    stage,
                    GraphRagEventSink.Outcome.SUCCEEDED,
                    Duration.ofNanos(Math.max(
                            0,
                            System.nanoTime() - startedAt)),
                    inputCount,
                    outputCount,
                    null,
                    null,
                    null,
                    null,
                    Instant.now()));
    }

    /**
     * Context assembly is the one retrieval stage whose cost is measured in
     * tokens rather than items, and the only stage that can report how much of
     * the retrieved context the budget refused to carry. Both numbers were being
     * computed and discarded.
     */
    private void emitAssembledContext(
            UUID operationId,
            UUID organizationId,
            long startedAt,
            int inputCount,
            LightRagGroundingAssembler.PreparedGrounding prepared,
            SecureContextBudget budget) {
        LightRagGrounding grounding = prepared.grounding();
        ContextTokenUsage usage = grounding.tokenUsage();
        safeEmit(new GraphRagEventSink.GraphRagEvent(
                    operationId,
                    organizationId,
                    GraphRagEventSink.Stage.ASSEMBLE_CONTEXT,
                    GraphRagEventSink.Outcome.SUCCEEDED,
                    Duration.ofNanos(Math.max(
                            0,
                            System.nanoTime() - startedAt)),
                    inputCount,
                    grounding.chunks().size(),
                    null,
                    null,
                    null,
                    null,
                    new GraphRagEventSink.TokenUsage(
                            prepared.inputTokens(),
                            usage.systemPromptTokens(),
                            usage.queryTokens(),
                            usage.entityTokens(),
                            usage.relationTokens(),
                            grounding.chunkTokens(),
                            budget.maximumInputTokens(),
                            prepared.droppedContributions()),
                    null,
                    Instant.now()));
    }

    private void emitPreparedStage(
            UUID operationId,
            UUID organizationId,
            GraphRagEventSink.Stage stage,
            Duration duration,
            int inputCount,
            int outputCount,
            String modelRouteFingerprint,
            GraphRagEventSink.CacheStatus cacheStatus) {
        safeEmit(new GraphRagEventSink.GraphRagEvent(
                    operationId,
                    organizationId,
                    stage,
                    GraphRagEventSink.Outcome.SUCCEEDED,
                    duration,
                    inputCount,
                    outputCount,
                    modelRouteFingerprint,
                    null,
                    cacheStatus,
                    null,
                    Instant.now()));
    }

    private void emitSnapshotStage(
            UUID operationId,
            UUID organizationId,
            Duration duration,
            int inputCount,
            int outputCount,
            ProjectionNamespace namespace) {
        String scopeFingerprint = CanonicalCacheKeyHasher.sha256(
                "orgmemory.graph-rag.snapshot-scope.v1",
                Map.of(
                        "organizationId",
                        namespace.organizationId().toString(),
                        "workspace",
                        namespace.workspace(),
                        "collection",
                        namespace.collection()));
        safeEmit(new GraphRagEventSink.GraphRagEvent(
                    operationId,
                    organizationId,
                    GraphRagEventSink.Stage.RETRIEVE_SNAPSHOT,
                    GraphRagEventSink.Outcome.SUCCEEDED,
                    duration,
                    inputCount,
                    outputCount,
                    null,
                    scopeFingerprint,
                    null,
                    null,
                    Instant.now()));
    }

    private record SnapshotQueryResult(
            LightRagQueryResult result,
            Duration duration,
            int inputCount,
            ProjectionNamespace namespace) {

        private SnapshotQueryResult {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(duration, "duration");
            if (duration.isNegative() || inputCount < 0) {
                throw new IllegalArgumentException(
                        "snapshot query metrics must be non-negative");
            }
            Objects.requireNonNull(namespace, "namespace");
        }
    }

    private record PublishedSpaceQuery(
            List<LightRagGrounding> groundings,
            KeywordPlan keywords) {

        private PublishedSpaceQuery {
            groundings = List.copyOf(Objects.requireNonNull(groundings, "groundings"));
            Objects.requireNonNull(keywords, "keywords");
        }

        private static PublishedSpaceQuery empty() {
            return new PublishedSpaceQuery(
                    List.of(),
                    KeywordPlan.empty(KeywordPlan.Source.MODEL));
        }
    }

    private record RetrievalRun(
            SecureKnowledgeSearchResult result,
            KeywordPlan keywords) {

        private RetrievalRun {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(keywords, "keywords");
        }

        private static RetrievalRun empty(String requestId) {
            return empty(
                    requestId,
                    KeywordPlan.empty(KeywordPlan.Source.MODEL));
        }

        private static RetrievalRun empty(
                String requestId,
                KeywordPlan keywords) {
            return new RetrievalRun(
                    new SecureKnowledgeSearchResult(requestId, List.of()),
                    keywords);
        }
    }

    private void verifyOpenFga(
            CurrentActor actor,
            String query,
            String requestId,
            String authorizationModelId,
            List<LightRagGrounding.GroundingEvidence> closure) {
        List<ResourceRef> resources = closure.stream()
                .map(candidate -> ResourceRef.of(
                        actor.organizationId(),
                        RESOURCE_TYPE,
                        candidate.evidence()
                                .knowledgeAssetId()))
                .distinct()
                .toList();
        var rechecked = batchRecheck.recheck(
                new BatchAuthorizationQuery(
                        actor.organizationId(),
                        actor.principal(),
                        CAN_VIEW,
                        resources),
                authorizationModelId,
                OpenFgaBatchRecheck.ResultPolicy.REQUIRE_ALL_ALLOWED,
                RECHECK_REASONS);
        if (!rechecked.succeeded()) {
            var failure = rechecked.failure();
            String failurePolicyVersion = switch (failure.kind()) {
                case MISSING_DECISION,
                        DECISION_POLICY_MISMATCH,
                        DENIED -> authorizationModelId;
                case UNRESOLVED,
                        DECISION_COUNT_MISMATCH,
                        OUTER_POLICY_MISMATCH -> failure.policyVersion();
            };
            throw searchAuthorization.unavailable(
                    actor,
                    requestId,
                    query,
                    failure.reasonCode(),
                    failurePolicyVersion);
        }
    }

    private List<SecureRetrievalCandidate> recheckCanonical(
            ResolvedKnowledgeEvidenceScope scope,
            List<LightRagGrounding.GroundingEvidence> closure) {
        return canonicalEvidence.recheck(
                scope.toRetrievalScope(),
                closure.stream()
                        .map(candidate -> candidate.evidence().chunkId())
                        .toList());
    }

    private RetrievalRun retryOrFail(
            CurrentActor actor,
            String query,
            LightRagQueryRequest.Options queryOptions,
            String requestId,
            String authorizationModelId,
            UUID operationId,
            int attempt,
            KnowledgeEvidenceSelection selection,
            String reason) {
        if (attempt == 0) {
            return search(
                    actor,
                    query,
                    queryOptions,
                    requestId,
                    authorizationModelId,
                    operationId,
                    1,
                    selection);
        }
        throw searchAuthorization.unavailable(
                actor,
                requestId,
                query,
                reason,
                authorizationModelId);
    }

    private static ResolvedKnowledgeEvidenceScope restrict(
            ResolvedKnowledgeEvidenceScope scope,
            KnowledgeEvidenceSelection selection) {
        return selection.restricted()
                ? scope.restrictTo(selection.assetIds())
                : scope;
    }

    private void emitRerank(
            UUID operationId,
            UUID organizationId,
            LightRagQueryResult result) {
        if (!result.trace().rerankAttempted()) {
            return;
        }
        GraphRagEventSink.Outcome outcome = result.trace().rerankFallback()
                ? GraphRagEventSink.Outcome.FAILED
                : GraphRagEventSink.Outcome.SUCCEEDED;
        String failureCode = result.trace().rerankFallback()
                ? "rerank_provider_fallback"
                : null;
        String routeFingerprint = CanonicalCacheKeyHasher.sha256(
                "reranker-route",
                Map.of("provider", policy.rerank().provider()));
        safeEmit(new GraphRagEventSink.GraphRagEvent(
                    operationId,
                    organizationId,
                    GraphRagEventSink.Stage.RERANK,
                    outcome,
                    result.trace().rerankDuration(),
                    result.trace().chunkSignals().size(),
                    result.grounding().chunks().size(),
                    routeFingerprint,
                    null,
                    null,
                    failureCode,
                    Instant.now()));
    }

    private void safeEmit(GraphRagEventSink.GraphRagEvent event) {
        try {
            events.emit(event);
        } catch (RuntimeException ignoredTelemetryFailure) {
            // Telemetry must never become a retrieval availability dependency.
        }
    }

    private static boolean sameAuthorizationScope(
            ResolvedKnowledgeEvidenceScope left,
            ResolvedKnowledgeEvidenceScope right) {
        if (!left.authorizationModelId()
                .equals(right.authorizationModelId())) {
            return false;
        }
        Set<UUID> spaces = new LinkedHashSet<>(
                left.knowledgeSpaceIds());
        spaces.addAll(right.knowledgeSpaceIds());
        return spaces.stream().allMatch(space -> left
                .forKnowledgeSpace(space)
                .authorizationFingerprint()
                .equals(right.forKnowledgeSpace(space)
                        .authorizationFingerprint()));
    }

    private static boolean sameEvidence(
            List<LightRagGrounding.GroundingEvidence> closure,
            List<SecureRetrievalCandidate> verified) {
        Map<UUID, SecureRetrievalCandidate> byChunk = verified.stream()
                .collect(Collectors.toMap(
                        SecureRetrievalCandidate::chunkId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (byChunk.size() != closure.size()) {
            return false;
        }
        return closure.stream().allMatch(candidate -> {
            EvidenceReference reference =
                    candidate.evidence();
            SecureRetrievalCandidate canonical =
                    byChunk.get(reference.chunkId());
            return canonical != null
                    && reference.organizationId()
                            .equals(canonical.organizationId())
                    && reference.knowledgeAssetId()
                            .equals(canonical.knowledgeAssetId())
                    && reference.sourceRevisionId()
                            .equals(canonical.sourceRevisionId())
                    && reference.aclSnapshotId()
                            .equals(canonical.ingestionAclSnapshotId())
                    && candidate.projectionGeneration()
                            == canonical.projectionGeneration();
        });
    }

    private static RetrievedKnowledgeEvidence toEvidence(
            SecureRetrievalCandidate candidate,
            double score) {
        return new RetrievedKnowledgeEvidence(
                candidate.chunkId(),
                candidate.knowledgeAssetId(),
                candidate.sourceObjectId(),
                candidate.sourceRevisionId(),
                candidate.title(),
                candidate.content(),
                SourceCitationUri.safeForOutput(candidate.sourceUri()),
                candidate.startPage(),
                candidate.endPage(),
                candidate.heading(),
                0.0,
                score,
                score,
                candidate.ingestionAclSnapshotId(),
                candidate.currentAclSnapshotId(),
                candidate.authorizationModelId(),
                candidate.embeddingProfileId(),
                candidate.projectionGeneration());
    }

    private static PermissionAuditCommand evidenceAudit(
            CurrentActor actor,
            String requestId,
            String query,
            SecureRetrievalCandidate candidate,
            String authorizationModelId,
            String reason) {
        return new PermissionAuditCommand(
                actor.organizationId(),
                actor.userId(),
                "SEARCH",
                "KNOWLEDGE_EVIDENCE",
                candidate.chunkId().toString(),
                PermissionAuditDecision.ALLOW,
                reason,
                authorizationModelId,
                requestId,
                query,
                candidate.ingestionAclSnapshotId(),
                candidate.currentAclSnapshotId(),
                candidate.authorizationModelId(),
                candidate.sourceRevisionId(),
                candidate.chunkId(),
                candidate.embeddingProfileId(),
                candidate.projectionGeneration());
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new BusinessValidationException(
                    "knowledge-search.query-required",
                    "q is required");
        }
        String normalized = query.strip();
        if (normalized.length()
                > retrievalProperties.maximumQueryLength()) {
            throw new BusinessValidationException(
                    "knowledge-search.query-invalid",
                    "q must not exceed "
                            + retrievalProperties.maximumQueryLength()
                            + " characters");
        }
        return normalized;
    }

    private int validateLimit(Integer requestedLimit) {
        int limit = requestedLimit == null
                ? Math.min(10, retrievalProperties.maximumResults())
                : requestedLimit;
        if (limit < 1
                || limit > retrievalProperties.maximumResults()) {
            throw new BusinessValidationException(
                    "knowledge-search.limit-invalid",
                    "limit must be between 1 and "
                            + retrievalProperties.maximumResults());
        }
        return limit;
    }

    private static String requestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String normalized = requestId.strip();
        return normalized.length() <= MAX_REQUEST_ID_LENGTH
                ? normalized
                : UUID.randomUUID().toString();
    }

    private static ProjectionNamespace namespace(
            UUID organizationId,
            UUID knowledgeSpaceId) {
        return KnowledgeProjectionNamespaces.forSpace(organizationId, knowledgeSpaceId);
    }

}
