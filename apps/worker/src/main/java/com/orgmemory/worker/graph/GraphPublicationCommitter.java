package com.orgmemory.worker.graph;

import com.orgmemory.core.knowledge.asset.KnowledgeProjectionNamespaces;
import com.orgmemory.core.knowledge.graph.ClaimedGraphIndex;
import com.orgmemory.core.knowledge.graph.GraphIndexChunk;
import com.orgmemory.core.knowledge.graph.GraphIndexingCoordinator;
import com.orgmemory.graphrag.cache.CanonicalCacheKeyHasher;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.model.ContributionEmbedding;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.port.GraphRevisionProjection;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.LexicalIndex;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionBatchLifecycle;
import com.orgmemory.graphrag.storage.ProjectionCommitPermit;
import com.orgmemory.graphrag.storage.ProjectionDiscardPermit;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.PublicationRebaseRequiredException;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import com.orgmemory.graphrag.storage.StagedProjectionWriter;
import com.orgmemory.graphrag.storage.VectorIndex;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/** Publishes every retrieval projection behind one compare-and-set visibility head. */
@Component
class GraphPublicationCommitter {

    private static final Set<ProjectionKind> REQUIRED_PROJECTIONS =
            Set.of(
                    ProjectionKind.CONTENT,
                    ProjectionKind.LEXICAL,
                    ProjectionKind.VECTOR,
                    ProjectionKind.GRAPH);

    private final GraphIndexingCoordinator coordinator;
    private final ProjectionPublicationStore publications;
    private final ContentStore content;
    private final LexicalIndex lexical;
    private final VectorIndex vectors;
    private final GraphStore graph;
    private final ModelInvocationCache modelCache;
    private final RetrievalResultCache retrievalCache;

    GraphPublicationCommitter(
            GraphIndexingCoordinator coordinator,
            ProjectionPublicationStore publications,
            ContentStore content,
            LexicalIndex lexical,
            VectorIndex vectors,
            GraphStore graph,
            ModelInvocationCache modelCache,
            RetrievalResultCache retrievalCache) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.publications = Objects.requireNonNull(publications, "publications");
        this.content = Objects.requireNonNull(content, "content");
        this.lexical = Objects.requireNonNull(lexical, "lexical");
        this.vectors = Objects.requireNonNull(vectors, "vectors");
        this.graph = Objects.requireNonNull(graph, "graph");
        this.modelCache = Objects.requireNonNull(modelCache, "modelCache");
        this.retrievalCache = Objects.requireNonNull(retrievalCache, "retrievalCache");
    }

    boolean completePublished(
            ClaimedGraphIndex claim,
            String workerId) {
        ProjectionNamespace namespace = namespace(claim);
        return publications
                .published(namespace, claim.idempotencyKey())
                .map(snapshot -> {
                    invalidate(namespace);
                    coordinator.completePublished(
                            claim.jobId(),
                            workerId,
                            claim.claimEpoch(),
                            snapshot);
                    return true;
                })
                .orElse(false);
    }

    void commit(
            ClaimedGraphIndex claim,
            String workerId,
            Duration renewedLeaseDuration,
            GraphRevisionProjection projection) {
        requireSameClaim(claim, projection);
        ProjectionNamespace namespace = namespace(claim);
        ProjectionSnapshot current = publications.current(namespace).orElse(null);
        long previousGeneration = current == null ? 0 : current.generation();
        Instant now = Instant.now();
        String manifestFingerprint = manifestFingerprint(claim, projection);
        Optional<ProjectionCommitPermit> recoveryPermit =
                coordinator.publicationPermit(claim.jobId());
        recoveryPermit.ifPresent(permit -> {
            if (!permit.manifestFingerprint().equals(manifestFingerprint)) {
                throw new IllegalStateException(
                        "durable graph publication permit has a different manifest");
            }
        });
        ProjectionBatch batch = new ProjectionBatch(
                recoveryPermit.map(ProjectionCommitPermit::batchId)
                        .orElseGet(() -> publicationAttemptId(claim, current)),
                namespace,
                current == null ? null : current.batchId(),
                previousGeneration,
                previousGeneration + 1,
                claim.idempotencyKey(),
                manifestFingerprint,
                REQUIRED_PROJECTIONS,
                recoveryPermit.map(ProjectionCommitPermit::claimEpoch)
                        .orElse(claim.claimEpoch()),
                now);

        List<ContentStore.ContentRecord> contentRecords = contentRecords(claim);
        List<LexicalIndex.LexicalDocument> lexicalDocuments =
                lexicalDocuments(claim);
        List<VectorIndex.VectorRecord> vectorRecords =
                vectorRecords(claim, projection);

        coordinator.preparePublication(
                claim.jobId(),
                workerId,
                renewedLeaseDuration,
                batch.manifestFingerprint());
        List<ProjectionBatchLifecycle.Preparation> preparations = List.of(
                        preparation(
                                content,
                                candidate -> {
                                    content.stageDeleteAsset(
                                            candidate, claim.knowledgeAssetId());
                                    content.stageUpsert(candidate, contentRecords);
                                }),
                        preparation(
                                lexical,
                                candidate -> {
                                    lexical.stageDeleteAsset(
                                            candidate, claim.knowledgeAssetId());
                                    lexical.stageUpsert(candidate, lexicalDocuments);
                                }),
                        preparation(
                                vectors,
                                candidate -> {
                                    vectors.stageDeleteAsset(
                                            candidate, claim.knowledgeAssetId());
                                    vectors.stageUpsert(candidate, vectorRecords);
                                }),
                        preparation(
                                graph,
                                candidate -> {
                                    graph.stageDeleteAsset(
                                            candidate, claim.knowledgeAssetId());
                                    graph.stageReplaceRevision(
                                            candidate, projection.contributions());
                                }));
        ProjectionSnapshot published;
        try {
            published = new ProjectionBatchLifecycle(publications).publish(
                    batch,
                    preparations,
                    registered -> coordinator.issueOrLoadPublicationPermit(
                            claim.jobId(), workerId, claim.claimEpoch(), registered),
                    recoveryPermit,
                    now);
        } catch (PublicationRebaseRequiredException rebase) {
            coordinator.retirePublicationPermit(
                    claim.jobId(),
                    workerId,
                    claim.claimEpoch(),
                    rebase.batch(),
                    rebase.discardPermit());
            discardAfterPermitRetirement(preparations, rebase);
            throw rebase;
        }
        invalidate(namespace);
        coordinator.completePublished(
                claim.jobId(), workerId, claim.claimEpoch(), published);
    }

    private void invalidate(ProjectionNamespace namespace) {
        modelCache.invalidate(namespace);
        retrievalCache.invalidateNamespace(namespace);
    }

    private static void discardAfterPermitRetirement(
            List<ProjectionBatchLifecycle.Preparation> preparations,
            PublicationRebaseRequiredException rebase) {
        for (int index = preparations.size() - 1; index >= 0; index--) {
            try {
                preparations.get(index).discard(
                        rebase.batch(), rebase.discardPermit());
            } catch (RuntimeException discardFailure) {
                rebase.addSuppressed(discardFailure);
            }
        }
    }

    private static ProjectionNamespace namespace(ClaimedGraphIndex claim) {
        return KnowledgeProjectionNamespaces.forSpace(
                claim.organizationId(), claim.knowledgeSpaceId());
    }

    private static List<ContentStore.ContentRecord> contentRecords(
            ClaimedGraphIndex claim) {
        return claim.chunks().stream()
                .map(chunk -> new ContentStore.ContentRecord(
                        chunk.id().toString(),
                        evidence(claim, chunk.id()),
                        ContentStore.ContentKind.CHUNK,
                        chunk.content(),
                        chunk.tokenCount(),
                        chunkMetadata(claim, chunk)))
                .toList();
    }

    private static List<LexicalIndex.LexicalDocument> lexicalDocuments(
            ClaimedGraphIndex claim) {
        return claim.chunks().stream()
                .map(chunk -> new LexicalIndex.LexicalDocument(
                        chunk.id().toString(),
                        evidence(claim, chunk.id()),
                        chunk.content(),
                        chunkMetadata(claim, chunk)))
                .toList();
    }

    private static List<VectorIndex.VectorRecord> vectorRecords(
            ClaimedGraphIndex claim,
            GraphRevisionProjection projection) {
        List<VectorIndex.VectorRecord> records = new ArrayList<>();
        for (GraphIndexChunk chunk : claim.chunks()) {
            records.add(new VectorIndex.VectorRecord(
                    vectorRecordId("chunk", chunk.id(), claim.embeddingProfile().id()),
                    chunk.id().toString(),
                    evidence(claim, chunk.id()),
                    VectorIndex.VectorKind.CHUNK,
                    claim.embeddingProfile().id(),
                    claim.embeddingProfile().model(),
                    chunk.embedding(),
                    chunkMetadata(claim, chunk)));
        }

        Map<UUID, EntityContribution> entities = projection.contributions().entities().stream()
                .collect(Collectors.toMap(EntityContribution::id, entity -> entity));
        for (ContributionEmbedding embedding : projection.embeddings().entityEmbeddings()) {
            EntityContribution contribution = requireContribution(
                    entities, embedding.contributionId(), "entity");
            records.add(new VectorIndex.VectorRecord(
                    vectorRecordId(
                            "entity",
                            contribution.id(),
                            claim.embeddingProfile().id()),
                    contribution.entity().id().toString(),
                    contribution.provenance().evidence(),
                    VectorIndex.VectorKind.ENTITY,
                    claim.embeddingProfile().id(),
                    claim.embeddingProfile().model(),
                    embedding.vector(),
                    Map.of(
                            "contributionId", contribution.id().toString(),
                            "entityType", contribution.type(),
                            "graphProcessingProfileSha256",
                                    claim.graphProcessingProfile()
                                            .canonicalSha256())));
        }

        Map<UUID, RelationContribution> relations =
                projection.contributions().relations().stream()
                        .collect(Collectors.toMap(
                                RelationContribution::id,
                                relation -> relation));
        for (ContributionEmbedding embedding : projection.embeddings().relationEmbeddings()) {
            RelationContribution contribution = requireContribution(
                    relations, embedding.contributionId(), "relation");
            records.add(new VectorIndex.VectorRecord(
                    vectorRecordId(
                            "relation",
                            contribution.id(),
                            claim.embeddingProfile().id()),
                    contribution.relation().id().toString(),
                    contribution.provenance().evidence(),
                    VectorIndex.VectorKind.RELATION,
                    claim.embeddingProfile().id(),
                    claim.embeddingProfile().model(),
                    embedding.vector(),
                    Map.of(
                            "contributionId", contribution.id().toString(),
                            "relationType", contribution.type(),
                            "graphProcessingProfileSha256",
                                    claim.graphProcessingProfile()
                                            .canonicalSha256())));
        }
        return List.copyOf(records);
    }

    private static <T> T requireContribution(
            Map<UUID, T> contributions,
            UUID contributionId,
            String kind) {
        T contribution = contributions.get(contributionId);
        if (contribution == null) {
            throw new IllegalArgumentException(
                    kind + " embedding has no matching contribution");
        }
        return contribution;
    }

    private static EvidenceReference evidence(
            ClaimedGraphIndex claim,
            UUID chunkId) {
        return new EvidenceReference(
                claim.organizationId(),
                claim.knowledgeAssetId(),
                claim.sourceRevisionId(),
                chunkId,
                claim.aclSnapshotId(),
                claim.aclGeneration());
    }

    private static Map<String, String> chunkMetadata(
            ClaimedGraphIndex claim,
            GraphIndexChunk chunk) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("chunkIndex", Integer.toString(chunk.index()));
        metadata.put(
                "graphProcessingProfileSha256",
                claim.graphProcessingProfile().canonicalSha256());
        metadata.put(
                ContentStore.ASSET_PROJECTION_GENERATION_METADATA_KEY,
                Long.toString(claim.projectionGeneration()));
        if (chunk.heading() != null) {
            metadata.put("heading", chunk.heading());
        }
        return Map.copyOf(metadata);
    }

    private static String vectorRecordId(
            String kind,
            UUID subjectId,
            UUID profileId) {
        return kind + ":" + subjectId + ":" + profileId;
    }

    private static UUID publicationAttemptId(
            ClaimedGraphIndex claim, ProjectionSnapshot predecessor) {
        String predecessorId = predecessor == null
                ? "none"
                : predecessor.batchId().toString();
        return UUID.nameUUIDFromBytes(String.join(
                        "|",
                        "orgmemory.graph-rag.publication-attempt.v1",
                        claim.jobId().toString(),
                        claim.idempotencyKey(),
                        predecessorId,
                        Long.toString(claim.claimEpoch()))
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String manifestFingerprint(
            ClaimedGraphIndex claim,
            GraphRevisionProjection projection) {
        String chunks = claim.chunks().stream()
                .sorted(Comparator.comparingInt(GraphIndexChunk::index)
                        .thenComparing(GraphIndexChunk::id))
                .map(chunk -> String.join(
                        "|",
                        chunk.id().toString(),
                        Integer.toString(chunk.index()),
                        Objects.toString(chunk.heading(), ""),
                        Integer.toString(chunk.tokenCount()),
                        chunk.content(),
                        vector(chunk.embedding())))
                .collect(Collectors.joining("\n"));
        return CanonicalCacheKeyHasher.sha256(
                "orgmemory.graph-rag.shared-publication.v1",
                Map.of(
                        "graphManifest", projection.manifestFingerprint(),
                        "chunks", chunks,
                        "embeddingProfileId", claim.embeddingProfile().id().toString(),
                        "embeddingProvider", claim.embeddingProfile().provider(),
                        "embeddingModel", claim.embeddingProfile().model(),
                        "embeddingDimensions",
                                Integer.toString(claim.embeddingProfile().dimensions()),
                        "graphProcessingProfileSha256",
                                claim.graphProcessingProfile().canonicalSha256()));
    }

    private static String vector(FloatVector vector) {
        return IntStream.range(0, vector.dimensions())
                .mapToObj(index -> Float.toHexString(vector.valueAt(index)))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static void requireSameClaim(
            ClaimedGraphIndex claim,
            GraphRevisionProjection projection) {
        if (!claim.organizationId().equals(projection.contributions().organizationId())
                || !claim.knowledgeAssetId().equals(
                        projection.contributions().knowledgeAssetId())
                || !claim.sourceRevisionId().equals(
                        projection.contributions().sourceRevisionId())
                || claim.projectionGeneration()
                        != projection.contributions().projectionGeneration()
                || !claim.idempotencyKey().equals(projection.idempotencyKey())
                || !claim.embeddingProfile().id().equals(
                        projection.embeddings().embeddingProfileId())
                || claim.embeddingProfile().dimensions()
                        != projection.embeddings().embeddingDimensions()) {
            throw new IllegalArgumentException(
                    "graph projection does not match the claimed indexing input");
        }
    }

    private static ProjectionBatchLifecycle.Preparation preparation(
            StagedProjectionWriter writer,
            Consumer<ProjectionBatch> stage) {
        return new ProjectionBatchLifecycle.Preparation() {
            @Override
            public ProjectionKind projectionKind() {
                return writer.projectionKind();
            }

            @Override
            public void prepare(ProjectionBatch batch) {
                stage.accept(batch);
            }

            @Override
            public void discard(
                    ProjectionBatch batch, ProjectionDiscardPermit permit) {
                writer.discard(batch, permit);
            }
        };
    }
}
