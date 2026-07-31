package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.sourceledger.SourceCitationUri;

import com.orgmemory.core.ai.ChatGenerationRequest;
import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
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
import com.orgmemory.graphrag.query.LightRagGrounding;
import com.orgmemory.graphrag.query.LightRagGroundingAssembler;
import com.orgmemory.graphrag.query.LightRagPreparedQuery;
import com.orgmemory.graphrag.query.LightRagQueryEngine;
import com.orgmemory.graphrag.query.LightRagQueryRequest;
import com.orgmemory.graphrag.query.LightRagQueryResult;
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
public class GraphRagKnowledgeRetrievalService
        implements PermissionAwareKnowledgeSearch {

    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final String RESOURCE_TYPE = "knowledge_asset";
    private static final int MAX_REQUEST_ID_LENGTH = 128;

    private final KnowledgeSearchAuthorizationService searchAuthorization;
    private final KnowledgeEvidenceScopeResolver evidenceScopes;
    private final RelationshipAuthorizationSetPort authorization;
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

    public GraphRagKnowledgeRetrievalService(
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
                GraphRagTaskDecorator.NONE);
    }

    public GraphRagKnowledgeRetrievalService(
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
        this.searchAuthorization = searchAuthorization;
        this.evidenceScopes = evidenceScopes;
        this.authorization = authorization;
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
    }

    @Override
    public SecureKnowledgeSearchResult search(
            CurrentActor actor,
            String query,
            Integer requestedLimit,
            String suppliedRequestId) {
        Objects.requireNonNull(actor, "actor");
        UUID operationId = UUID.randomUUID();
        long startedAt = System.nanoTime();
        try {
            String requestId = requestId(suppliedRequestId);
            String normalizedQuery = normalizeQuery(query);
            int limit = validateLimit(requestedLimit);
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
            SecureKnowledgeSearchResult result = search(
                    actor,
                    normalizedQuery,
                    limit,
                    requestId,
                    authorizationModelId,
                    operationId,
                    0);
            emit(
                    operationId,
                    actor.organizationId(),
                    GraphRagEventSink.Outcome.SUCCEEDED,
                    startedAt,
                    result.evidence().size(),
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

    private void emit(
            UUID operationId,
            UUID organizationId,
            GraphRagEventSink.Outcome outcome,
            long startedAt,
            int outputCount,
            String failureCode) {
        try {
            events.emit(new GraphRagEventSink.GraphRagEvent(
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
        } catch (RuntimeException ignoredTelemetryFailure) {
            // Telemetry must never become a retrieval availability dependency.
        }
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

    private SecureKnowledgeSearchResult search(
            CurrentActor actor,
            String query,
            int limit,
            String requestId,
            String authorizationModelId,
            UUID operationId,
            int attempt) {
        ResolvedKnowledgeEvidenceScope initial =
                resolve(
                        actor,
                        authorizationModelId,
                        requestId,
                        query,
                        operationId);
        if (initial.allAssetIds().isEmpty()) {
            audit.record(searchAuthorization.command(
                    actor,
                    requestId,
                    query,
                    PermissionAuditDecision.ALLOW,
                    "NO_AUTHORIZED_KNOWLEDGE_ASSETS",
                    initial.authorizationModelId()));
            return new SecureKnowledgeSearchResult(requestId, List.of());
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

        LightRagQueryRequest.Options queryOptions =
                policy.contextOptions(limit);
        List<LightRagGrounding> spaceGroundings =
                queryPublishedSpaces(
                        initial,
                        profile,
                        query,
                        queryOptions,
                        operationId);
        if (spaceGroundings.isEmpty()) {
            audit.record(searchAuthorization.command(
                    actor,
                    requestId,
                    query,
                    PermissionAuditDecision.ALLOW,
                    "NO_ELIGIBLE_EVIDENCE",
                    initial.authorizationModelId()));
            return new SecureKnowledgeSearchResult(requestId, List.of());
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
            return new SecureKnowledgeSearchResult(requestId, List.of());
        }

        ResolvedKnowledgeEvidenceScope current =
                resolve(
                        actor,
                        authorizationModelId,
                        requestId,
                        query,
                        operationId);
        if (!sameAuthorizationScope(initial, current)) {
            return retryOrFail(
                    actor,
                    query,
                    limit,
                    requestId,
                    authorizationModelId,
                    operationId,
                    attempt,
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
                    limit,
                    requestId,
                    authorizationModelId,
                    operationId,
                    attempt,
                    "CANONICAL_EVIDENCE_CHANGED");
        }
        LightRagGroundingAssembler.PreparedGrounding rendered =
                engine.renderGrounding(
                        query,
                        queryOptions,
                        consolidated.grounding());

        Map<UUID, SecureRetrievalCandidate> canonicalByChunk = verified.stream()
                .collect(Collectors.toMap(
                        SecureRetrievalCandidate::chunkId,
                        Function.identity()));
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
        return new SecureKnowledgeSearchResult(
                requestId,
                evidence,
                Optional.of(new VerifiedKnowledgeGrounding(
                        new ChatGenerationRequest(
                                rendered.systemPrompt(),
                                query),
                        evidence,
                        closure.size(),
                        rendered.inputTokens())));
    }

    private List<LightRagGrounding> queryPublishedSpaces(
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
            return List.of();
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
            for (int offset = 0;
                    offset < requests.size();
                    offset += policy.maximumConcurrentSpaces()) {
                int end = Math.min(
                        requests.size(),
                        offset + policy.maximumConcurrentSpaces());
                List<Future<SnapshotQueryResult>> futures =
                        requests.subList(offset, end)
                                .stream()
                                .map(request -> executor.submit(tasks.decorate(() ->
                                        queryPublishedSpace(request, prepared))))
                                .toList();
                try {
                    for (Future<SnapshotQueryResult> future : futures) {
                        SnapshotQueryResult snapshotResult = future.get();
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
                } catch (ExecutionException | InterruptedException
                        | RuntimeException failure) {
                    futures.forEach(future -> future.cancel(true));
                    throw retrievalFailure(failure);
                }
            }
        }
        return List.copyOf(groundings);
    }

    private SnapshotQueryResult queryPublishedSpace(
            LightRagQueryRequest request,
            LightRagPreparedQuery prepared) {
        long startedAt = System.nanoTime();
        LightRagQueryResult result =
                engine.executePrepared(request, prepared);
        return new SnapshotQueryResult(
                result,
                Duration.ofNanos(Math.max(
                        0,
                        System.nanoTime() - startedAt)),
                request.scope().authorizedAssetIds().size(),
                request.snapshot().namespace());
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
        try {
            events.emit(new GraphRagEventSink.GraphRagEvent(
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
        } catch (RuntimeException ignoredTelemetryFailure) {
            // Telemetry must never become a retrieval availability dependency.
        }
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
        try {
            events.emit(new GraphRagEventSink.GraphRagEvent(
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
        } catch (RuntimeException ignoredTelemetryFailure) {
            // Telemetry must never become a retrieval availability dependency.
        }
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
        try {
            events.emit(new GraphRagEventSink.GraphRagEvent(
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
        } catch (RuntimeException ignoredTelemetryFailure) {
            // Telemetry must never become a retrieval availability dependency.
        }
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
        try {
            events.emit(new GraphRagEventSink.GraphRagEvent(
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
        } catch (RuntimeException ignoredTelemetryFailure) {
            // Telemetry must never become a retrieval availability dependency.
        }
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
        var checked = authorization.batchCheck(
                new BatchAuthorizationQuery(
                        actor.organizationId(),
                        actor.principal(),
                        CAN_VIEW,
                        resources));
        if (!checked.resolved()
                || !authorizationModelId.equals(checked.policyVersion())
                || checked.decisions().size() != resources.size()) {
            throw searchAuthorization.unavailable(
                    actor,
                    requestId,
                    query,
                    checked.reasonCode(),
                    checked.policyVersion());
        }
        for (ResourceRef resource : resources) {
            var decision = checked.decisions().get(resource);
            if (decision == null
                    || !decision.allowed()
                    || !authorizationModelId.equals(
                            decision.policyVersion())) {
                throw searchAuthorization.unavailable(
                        actor,
                        requestId,
                        query,
                        "FINAL_OPENFGA_RECHECK_DENIED",
                        checked.policyVersion());
            }
        }
    }

    private List<SecureRetrievalCandidate> recheckCanonical(
            ResolvedKnowledgeEvidenceScope scope,
            List<LightRagGrounding.GroundingEvidence> closure) {
        return canonicalEvidence.recheck(
                new SecureKnowledgeRetrievalStore.RetrievalScope(
                        scope.organizationId(),
                        scope.actorUserId(),
                        scope.actorDepartmentId(),
                        scope.actorExecutive(),
                        scope.allAssetIds().stream().sorted().toList(),
                        scope.authorizationModelId(),
                        scope.evaluatedAt()),
                closure.stream()
                        .map(candidate -> candidate.evidence().chunkId())
                        .toList());
    }

    private SecureKnowledgeSearchResult retryOrFail(
            CurrentActor actor,
            String query,
            int limit,
            String requestId,
            String authorizationModelId,
            UUID operationId,
            int attempt,
            String reason) {
        if (attempt == 0) {
            return search(
                    actor,
                    query,
                    limit,
                    requestId,
                    authorizationModelId,
                    operationId,
                    1);
        }
        throw searchAuthorization.unavailable(
                actor,
                requestId,
                query,
                reason,
                authorizationModelId);
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
        try {
            events.emit(new GraphRagEventSink.GraphRagEvent(
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
        return new ProjectionNamespace(
                organizationId,
                "default",
                knowledgeSpaceId.toString());
    }

}
