package com.orgmemory.worker.ingestion;

import com.orgmemory.worker.WorkProcessingResult;
import com.orgmemory.core.knowledge.acl.AclCaptureStatus;
import com.orgmemory.core.knowledge.acl.SourceAclEntryCommand;
import com.orgmemory.core.knowledge.acl.SourcePrincipalType;

import com.orgmemory.core.knowledge.sourceledger.ClaimedSourceRevision;
import com.orgmemory.core.knowledge.sourceledger.DocumentProcessingProfileSnapshot;
import com.orgmemory.core.knowledge.sourceledger.KnowledgeIngestionService;
import com.orgmemory.core.knowledge.sourceledger.NormalizeRawSourceCommand;
import com.orgmemory.core.knowledge.sourceledger.NormalizedRecordRef;
import com.orgmemory.core.knowledge.sourceledger.RawSourceRef;
import com.orgmemory.core.knowledge.sourceledger.RegisterRawSourceCommand;
import com.orgmemory.core.knowledge.sourceledger.ProcessingProfileMismatchException;
import com.orgmemory.core.knowledge.sourceledger.SourceIngestionCoordinator;
import com.orgmemory.core.knowledge.sourceledger.SourceEmbeddingProfileRef;
import com.orgmemory.core.knowledge.sourceledger.SourceKnowledgeAssetRef;
import com.orgmemory.core.knowledge.sourceledger.SourceRevisionStatus;

import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.knowledge.retrieval.EmbeddingDistanceMetric;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRegistry;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileSpec;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetRef;
import com.orgmemory.core.knowledge.asset.KnowledgeEmbeddingProfileRef;
import com.orgmemory.core.knowledge.asset.KnowledgeChunkDraftAssembler;
import com.orgmemory.core.knowledge.asset.KnowledgeTextChunk;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetPublicationService;
import com.orgmemory.core.knowledge.asset.PublishKnowledgeAssetCommand;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.graphrag.observability.GraphRagEventSink;
import com.orgmemory.graphrag.parsing.DocumentParseRequest;
import com.orgmemory.graphrag.parsing.DocumentParseResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class SourceIngestionProcessor {

    private static final Logger log = LoggerFactory.getLogger(SourceIngestionProcessor.class);
    private static final TokenCountBatchingStrategy BATCHING_STRATEGY =
            new TokenCountBatchingStrategy();

    private final SourceIngestionCoordinator coordinator;
    private final KnowledgeIngestionService ingestion;
    private final KnowledgeAssetPublicationService publications;
    private final EmbeddingProfileRegistry embeddingProfiles;
    private final ObjectStoragePort objects;
    private final ObjectProvider<EmbeddingModel> embeddingModels;
    private final AiRouteResolver aiRoutes;
    private final SourceProcessingProperties properties;
    private final DocumentProcessingEngine processingEngine;
    private final GraphRagEventSink events;

    @Autowired
    SourceIngestionProcessor(
            SourceIngestionCoordinator coordinator,
            KnowledgeIngestionService ingestion,
            KnowledgeAssetPublicationService publications,
            EmbeddingProfileRegistry embeddingProfiles,
            ObjectStoragePort objects,
            ObjectProvider<EmbeddingModel> embeddingModels,
            AiRouteResolver aiRoutes,
            SourceProcessingProperties properties,
            DocumentProcessingEngine processingEngine,
            ObjectProvider<GraphRagEventSink> eventSinks) {
        this(
                coordinator,
                ingestion,
                publications,
                embeddingProfiles,
                objects,
                embeddingModels,
                aiRoutes,
                properties,
                processingEngine,
                GraphRagEventSink.failureTolerant(
                        GraphRagEventSink.composite(eventSinks.orderedStream().toList())));
    }

    SourceIngestionProcessor(
            SourceIngestionCoordinator coordinator,
            KnowledgeIngestionService ingestion,
            KnowledgeAssetPublicationService publications,
            EmbeddingProfileRegistry embeddingProfiles,
            ObjectStoragePort objects,
            ObjectProvider<EmbeddingModel> embeddingModels,
            AiRouteResolver aiRoutes,
            SourceProcessingProperties properties,
            DocumentProcessingEngine processingEngine,
            GraphRagEventSink events) {
        this.coordinator = coordinator;
        this.ingestion = ingestion;
        this.publications = publications;
        this.embeddingProfiles = embeddingProfiles;
        this.objects = objects;
        this.embeddingModels = embeddingModels;
        this.aiRoutes = aiRoutes;
        this.properties = properties;
        this.processingEngine = processingEngine;
        this.events = Objects.requireNonNull(events, "events");
    }

    WorkProcessingResult processNext() {
        return coordinator.claimNext(
                        properties.workerId(),
                        properties.leaseDuration(),
                        processingEngine.requestedProcessingProfile())
                .map(claim -> {
                    process(claim);
                    return WorkProcessingResult.PROCESSED;
                })
                .orElse(WorkProcessingResult.EMPTY_OR_DEFERRED);
    }

    /**
     * Reports the two ingestion stages that had no producer.
     *
     * <p>The revision status already moved through {@code PARSING} and {@code CHUNKING}, but a
     * status says where a job is, not how long it stayed there — so a document that took four
     * minutes to parse and one that took four seconds left the same trace. These are emitted
     * from the same {@code jobId} the graph indexing stages use, so one upload reads as one
     * operation across both processors.
     */
    private void emitStage(
            ClaimedSourceRevision claim,
            GraphRagEventSink.Stage stage,
            Duration duration,
            int inputCount,
            int outputCount) {
        try {
            events.emit(new GraphRagEventSink.GraphRagEvent(
                    claim.jobId(),
                    claim.organizationId(),
                    stage,
                    GraphRagEventSink.Outcome.SUCCEEDED,
                    duration,
                    inputCount,
                    outputCount,
                    null,
                    null,
                    null,
                    null,
                    Instant.now()));
        } catch (RuntimeException ignoredTelemetryFailure) {
            // Telemetry must never become an ingestion availability dependency.
        }
    }

    private void process(ClaimedSourceRevision claim) {
        String failureStage = "VALIDATION";
        Path temporaryFile = null;
        try {
            RequestedProcessingPolicy policy =
                    processingEngine.requestedPolicy(claim.requestedProcessingProfile());
            var embeddingRoute = aiRoutes.resolve(AiWorkload.DOCUMENT_EMBEDDING);
            if (!embeddingRoute.modelId().equals(policy.embeddingModel())) {
                throw new IllegalStateException(
                        "Document embedding route does not match the immutable embedding profile model");
            }
            EmbeddingModel embeddingModel = embeddingModels.getIfAvailable();
            if (embeddingModel == null) {
                coordinator.fail(
                        claim.jobId(),
                        properties.workerId(),
                        "EMBEDDING_MODEL_UNAVAILABLE",
                        "Embedding is not configured for the ingestion worker",
                        true,
                        false);
                return;
            }

            temporaryFile = Files.createTempFile("orgmemory-ingestion-", fileSuffix(claim.fileName()));
            copyAndVerify(claim, temporaryFile);
            coordinator.markEvidenceValidated(claim.jobId(), properties.workerId());
            coordinator.markStage(
                    claim.jobId(), properties.workerId(), SourceRevisionStatus.PARSING, properties.leaseDuration());

            failureStage = "PARSING";
            ProcessedSourceDocument processed = processingEngine.process(
                    new DocumentParseRequest(
                            claim.fileName(),
                            claim.mediaType(),
                            Files.readAllBytes(temporaryFile),
                            Optional.empty()),
                    embeddingModel,
                    claim.requestedProcessingProfile(),
                    claim.resolvedProcessingProfile());
            DocumentParseResult parsed = processed.parseResult();
            emitStage(
                    claim,
                    GraphRagEventSink.Stage.PARSE,
                    processed.parseDuration(),
                    1,
                    parsed.document().blocks().size());
            emitStage(
                    claim,
                    GraphRagEventSink.Stage.CHUNK,
                    processed.chunkDuration(),
                    parsed.document().blocks().size(),
                    processed.chunks().size());
            DocumentProcessingProfileSnapshot resolvedProcessingProfile =
                    new DocumentProcessingProfileSnapshot(
                            processed.profile().canonicalForm(),
                            processed.profile().profileSha256());
            coordinator.bindResolvedProcessingProfile(
                    claim.jobId(),
                    properties.workerId(),
                    resolvedProcessingProfile);
            RawSourceRef raw = registerRawSource(claim, parsed);
            NormalizedRecordRef normalized = ingestion.normalize(new NormalizeRawSourceCommand(
                    claim.organizationId(),
                    raw.rawSourceObjectId(),
                    policy.normalizerVersion(),
                    claim.fileName(),
                    parsed.document().content(),
                    "und"));

            coordinator.markStage(
                    claim.jobId(), properties.workerId(), SourceRevisionStatus.CHUNKING, properties.leaseDuration());
            failureStage = "CHUNKING";
            List<KnowledgeTextChunk> candidates = processed.chunks().stream()
                    .map(chunk -> new KnowledgeTextChunk(
                            chunk.content(),
                            chunk.provenance().startPage(),
                            chunk.provenance().endPage(),
                            chunk.tokenCount(),
                            chunk.heading(),
                            chunk.provenance().startChar(),
                            chunk.provenance().endChar(),
                            chunk.provenance().blockIndexes(),
                            chunk.provenance().canonicalTextSha256()))
                    .toList();

            coordinator.markStage(
                    claim.jobId(), properties.workerId(), SourceRevisionStatus.EMBEDDING, properties.leaseDuration());
            failureStage = "EMBEDDING";
            EmbeddingProfileRef embeddingProfile = embeddingProfiles.resolve(
                    claim.organizationId(),
                    new EmbeddingProfileSpec(
                            policy.embeddingProvider(),
                            policy.embeddingModel(),
                            policy.embeddingDimensions(),
                            EmbeddingDistanceMetric.COSINE));
            List<Document> embeddingDocuments = candidates.stream()
                    .map(candidate -> new Document(candidate.content()))
                    .toList();
            List<float[]> vectors = embeddingModel.embed(
                    embeddingDocuments, null, BATCHING_STRATEGY);
            var drafts = KnowledgeChunkDraftAssembler.assemble(
                    candidates, vectors, embeddingProfile.dimensions());

            coordinator.markStage(
                    claim.jobId(), properties.workerId(), SourceRevisionStatus.PUBLISHING, properties.leaseDuration());
            failureStage = "PUBLISHING";
            KnowledgeAssetRef asset = publications.publish(new PublishKnowledgeAssetCommand(
                    claim.organizationId(),
                    claim.knowledgeSpaceId(),
                    claim.sourceObjectId(),
                    claim.sourceRevisionId(),
                    normalized.normalizedRecordId(),
                    claim.createdByUserId(),
                    new KnowledgeEmbeddingProfileRef(
                            embeddingProfile.id(),
                            embeddingProfile.organizationId(),
                            embeddingProfile.dimensions()),
                    policy.pipelineVersion(),
                    drafts));
            coordinator.complete(
                    claim.jobId(),
                    properties.workerId(),
                    policy.pipelineVersion(),
                    processed.profile().actualParser().toString(),
                    processed.profile().actualChunker().toString(),
                    resolvedProcessingProfile,
                    new SourceEmbeddingProfileRef(
                            embeddingProfile.id(), embeddingProfile.dimensions()),
                    raw,
                    normalized,
                    new SourceKnowledgeAssetRef(
                            asset.knowledgeAssetId(),
                            asset.knowledgeAssetVersionId(),
                            asset.normalizedRecordId(),
                            asset.rawSourceObjectId(),
                            asset.sourceAclSnapshotId()));
            log.info(
                    "Source revision {} is ready with {} chunks on attempt {}",
                    claim.sourceRevisionId(),
                    drafts.size(),
                    claim.attempt());
        } catch (ProcessingProfileMismatchException mismatch) {
            log.error(
                    "Source revision {} no longer matches its pinned processing profile",
                    claim.sourceRevisionId(),
                    mismatch);
            coordinator.fail(
                    claim.jobId(),
                    properties.workerId(),
                    "PROCESSING_PROFILE_MISMATCH",
                    "The immutable processing profile did not reproduce",
                    false,
                    false);
        } catch (RejectedSourceException rejected) {
            log.info("Source revision {} was quarantined: {}", claim.sourceRevisionId(), rejected.code());
            coordinator.fail(
                    claim.jobId(),
                    properties.workerId(),
                    rejected.code(),
                    rejected.getMessage(),
                    false,
                    true);
        } catch (Exception failure) {
            log.error("Source revision {} failed at {}", claim.sourceRevisionId(), failureStage, failure);
            coordinator.fail(
                    claim.jobId(),
                    properties.workerId(),
                    failureStage + "_FAILED",
                    "The " + failureStage.toLowerCase() + " stage failed; retry is scheduled",
                    true,
                    false);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException cleanupFailure) {
                    log.warn("Could not remove temporary ingestion file for revision {}", claim.sourceRevisionId());
                }
            }
        }
    }

    private void copyAndVerify(ClaimedSourceRevision claim, Path temporaryFile) throws IOException {
        MessageDigest digest = sha256Digest();
        long copied;
        try (var object = objects.open(new ObjectKey(claim.objectKey()));
                InputStream content = new DigestInputStream(object.stream(), digest)) {
            copied = Files.copy(content, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
        }
        if (copied != claim.contentLength()) {
            throw new RejectedSourceException("CONTENT_LENGTH_MISMATCH", "Stored object length is invalid");
        }
        String actualSha = HexFormat.of().formatHex(digest.digest());
        if (!actualSha.equals(claim.contentSha256())) {
            throw new RejectedSourceException("CONTENT_HASH_MISMATCH", "Stored object integrity check failed");
        }
    }

    private RawSourceRef registerRawSource(
            ClaimedSourceRevision claim,
            DocumentParseResult parsed) {
        return ingestion.registerRawSource(new RegisterRawSourceCommand(
                claim.organizationId(),
                claim.departmentId(),
                "UPLOAD",
                claim.sourceConnectionKey(),
                claim.externalObjectId(),
                "1",
                parsed.detectedMediaType(),
                claim.fileName(),
                parsed.document().content(),
                null,
                claim.createdAt(),
                claim.classification(),
                claim.declaredAccess(),
                AclCaptureStatus.COMPLETE,
                AccessGate.DENY,
                claim.createdAt().plus(Duration.ofHours(23)),
                sourceAcl(claim)));
    }

    private static List<SourceAclEntryCommand> sourceAcl(ClaimedSourceRevision claim) {
        return switch (claim.declaredAccess()) {
            case ALL, ALL_EMPLOYEES, EXECUTIVE_ONLY -> List.of(new SourceAclEntryCommand(
                    SourcePrincipalType.ORGMEMORY_ORGANIZATION,
                    claim.organizationId().toString(),
                    AccessGate.ALLOW));
            case OWN_DEPARTMENT -> {
                if (claim.departmentId() == null) {
                    throw new IllegalStateException(
                            "Department-scoped knowledge requires a department Knowledge Space");
                }
                yield List.of(new SourceAclEntryCommand(
                        SourcePrincipalType.ORGMEMORY_DEPARTMENT,
                        claim.departmentId().toString(),
                        AccessGate.ALLOW));
            }
        };
    }

    private static String fileSuffix(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? ".bin" : fileName.substring(dot);
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

}
