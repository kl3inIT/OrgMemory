package com.orgmemory.worker.graph;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.knowledge.ClaimedGraphIndex;
import com.orgmemory.core.knowledge.GraphIndexChunk;
import com.orgmemory.core.knowledge.GraphIndexingCoordinator;
import com.orgmemory.graphrag.indexing.ExtractedChunk;
import com.orgmemory.graphrag.indexing.GraphContributionAssembler;
import com.orgmemory.graphrag.model.ContributionEmbedding;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.ExtractionProfile;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.port.EntityRelationExtractor;
import com.orgmemory.graphrag.port.GraphRevisionEmbeddings;
import com.orgmemory.graphrag.port.GraphRevisionProjection;
import com.orgmemory.integrations.graphrag.springai.SpringAiEntityRelationExtractor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class GraphIndexingProcessor {

    private static final Logger log = LoggerFactory.getLogger(GraphIndexingProcessor.class);

    private final GraphIndexingCoordinator coordinator;
    private final GraphPublicationCommitter publications;
    private final GraphExtractorFactory extractors;
    private final ObjectProvider<EmbeddingModel> embeddingModels;
    private final AiRouteResolver routes;
    private final GraphIndexingProperties properties;

    GraphIndexingProcessor(
            GraphIndexingCoordinator coordinator,
            GraphPublicationCommitter publications,
            GraphExtractorFactory extractors,
            ObjectProvider<EmbeddingModel> embeddingModels,
            AiRouteResolver routes,
            GraphIndexingProperties properties) {
        this.coordinator = coordinator;
        this.publications = publications;
        this.extractors = extractors;
        this.embeddingModels = embeddingModels;
        this.routes = routes;
        this.properties = properties;
    }

    void processNext() {
        coordinator.claimNext(properties.workerId(), properties.leaseDuration())
                .ifPresent(this::process);
    }

    private void process(ClaimedGraphIndex claim) {
        try {
            AiRoute extractionRoute = routes.resolve(AiWorkload.GRAPH_EXTRACTION);
            ExtractionProfile extractionProfile = new ExtractionProfile(
                    extractionRoute.gatewayId(),
                    extractionRoute.modelId(),
                    SpringAiEntityRelationExtractor.PROMPT_VERSION,
                    properties.maximumEntitiesPerChunk(),
                    properties.maximumRelationsPerChunk());
            EntityRelationExtractor extractor = extractors.create(extractionRoute);
            List<ExtractedChunk> extracted = extractChunks(claim, extractionProfile, extractor);
            var contributions = GraphContributionAssembler.assemble(
                    claim.organizationId(),
                    claim.knowledgeAssetId(),
                    claim.sourceRevisionId(),
                    claim.aclSnapshotId(),
                    claim.aclGeneration(),
                    claim.projectionGeneration(),
                    Instant.now(),
                    extracted);
            GraphRevisionEmbeddings embeddings = embed(claim, contributions.entities(), contributions.relations());
            publications.commit(
                    claim.jobId(),
                    properties.workerId(),
                    properties.leaseDuration(),
                    new GraphRevisionProjection(contributions, embeddings));
            log.info(
                    "Published graph generation {} for Knowledge Asset version {} with {} entities and {} relations",
                    claim.projectionGeneration(),
                    claim.knowledgeAssetVersionId(),
                    contributions.entities().size(),
                    contributions.relations().size());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn(
                    "Graph indexing interrupted for job {}; leaving its lease to expire for retry",
                    claim.jobId());
        } catch (Exception failure) {
            log.error(
                    "Graph indexing failed for Knowledge Asset version {} on attempt {}",
                    claim.knowledgeAssetVersionId(),
                    claim.attempt(),
                    failure);
            recordFailure(claim);
        }
    }

    private List<ExtractedChunk> extractChunks(
            ClaimedGraphIndex claim,
            ExtractionProfile profile,
            EntityRelationExtractor extractor)
            throws ExecutionException, InterruptedException {
        List<ExtractedChunk> extracted = new ArrayList<>(claim.chunks().size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int offset = 0; offset < claim.chunks().size(); offset += properties.maximumConcurrency()) {
                int end = Math.min(
                        claim.chunks().size(), offset + properties.maximumConcurrency());
                List<Future<ExtractedChunk>> futures = claim.chunks().subList(offset, end).stream()
                        .map(chunk -> executor.submit(() -> extract(claim, profile, extractor, chunk)))
                        .toList();
                try {
                    for (Future<ExtractedChunk> future : futures) {
                        extracted.add(awaitWithLeaseHeartbeat(claim, future));
                    }
                } catch (ExecutionException | InterruptedException | RuntimeException failure) {
                    futures.forEach(future -> future.cancel(true));
                    throw failure;
                }
                coordinator.refreshLease(
                        claim.jobId(), properties.workerId(), properties.leaseDuration());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
        return List.copyOf(extracted);
    }

    private ExtractedChunk awaitWithLeaseHeartbeat(
            ClaimedGraphIndex claim, Future<ExtractedChunk> future)
            throws ExecutionException, InterruptedException {
        long heartbeatMillis =
                Math.max(1, properties.leaseDuration().dividedBy(3).toMillis());
        while (true) {
            try {
                return future.get(heartbeatMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                coordinator.refreshLease(
                        claim.jobId(), properties.workerId(), properties.leaseDuration());
            }
        }
    }

    private void recordFailure(ClaimedGraphIndex claim) {
        try {
            coordinator.fail(
                    claim.jobId(),
                    properties.workerId(),
                    "GRAPH_INDEX_FAILED",
                    "Graph extraction or publication failed; retry is scheduled");
        } catch (IllegalStateException lostLease) {
            log.warn(
                    "Graph indexing job {} lost its lease before failure could be recorded; "
                            + "the durable lease timeout will make it retryable",
                    claim.jobId());
        }
    }

    private static ExtractedChunk extract(
            ClaimedGraphIndex claim,
            ExtractionProfile profile,
            EntityRelationExtractor extractor,
            GraphIndexChunk chunk) {
        var result = extractor.extract(new com.orgmemory.graphrag.model.ExtractionRequest(
                claim.organizationId(),
                claim.knowledgeAssetId(),
                claim.sourceRevisionId(),
                chunk.id(),
                chunk.content(),
                Locale.forLanguageTag(claim.language()),
                profile));
        return new ExtractedChunk(chunk.id(), result);
    }

    private GraphRevisionEmbeddings embed(
            ClaimedGraphIndex claim,
            List<EntityContribution> entities,
            List<RelationContribution> relations) {
        AiRoute route = routes.resolve(AiWorkload.DOCUMENT_EMBEDDING);
        if (!route.gatewayId().equals(claim.embeddingProfile().provider())
                || !route.modelId().equals(claim.embeddingProfile().model())) {
            throw new IllegalStateException(
                    "Graph embeddings must use the immutable Knowledge Asset embedding profile");
        }
        List<Document> documents = new ArrayList<>(entities.size() + relations.size());
        entities.stream()
                .map(GraphIndexingProcessor::embeddingDocument)
                .forEach(documents::add);
        relations.stream()
                .map(GraphIndexingProcessor::embeddingDocument)
                .forEach(documents::add);
        List<float[]> vectors;
        if (documents.isEmpty()) {
            vectors = List.of();
        } else {
            EmbeddingModel model = embeddingModels.getIfAvailable();
            if (model == null) {
                throw new IllegalStateException("Embedding model is unavailable for graph indexing");
            }
            vectors = model.embed(documents, null, new TokenCountBatchingStrategy());
        }
        if (vectors.size() != documents.size()) {
            throw new IllegalStateException("Graph embedding response count does not match contributions");
        }
        List<ContributionEmbedding> entityEmbeddings = new ArrayList<>(entities.size());
        List<ContributionEmbedding> relationEmbeddings = new ArrayList<>(relations.size());
        int vectorIndex = 0;
        for (EntityContribution entity : entities) {
            entityEmbeddings.add(new ContributionEmbedding(
                    entity.id(), vector(vectors.get(vectorIndex++))));
        }
        for (RelationContribution relation : relations) {
            relationEmbeddings.add(new ContributionEmbedding(
                    relation.id(), vector(vectors.get(vectorIndex++))));
        }
        return new GraphRevisionEmbeddings(
                claim.organizationId(),
                claim.knowledgeAssetId(),
                claim.sourceRevisionId(),
                claim.projectionGeneration(),
                claim.embeddingProfile().id(),
                claim.embeddingProfile().dimensions(),
                entityEmbeddings,
                relationEmbeddings);
    }

    private static Document embeddingDocument(EntityContribution contribution) {
        return new Document("%s\n%s\n%s".formatted(
                contribution.entity().normalizedName(),
                contribution.entity().type(),
                contribution.description()));
    }

    private static Document embeddingDocument(RelationContribution contribution) {
        return new Document("%s\n%s\n%s".formatted(
                contribution.relation().type(),
                String.join(", ", contribution.keywords()),
                contribution.description()));
    }

    private static List<Float> vector(float[] values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return List.copyOf(result);
    }
}
