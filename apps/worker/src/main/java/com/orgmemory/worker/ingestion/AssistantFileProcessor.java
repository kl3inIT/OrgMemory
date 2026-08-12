package com.orgmemory.worker.ingestion;

import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.assistant.AssistantFileChunkDraft;
import com.orgmemory.core.assistant.AssistantFileProcessingClaim;
import com.orgmemory.core.assistant.AssistantFileProcessingProfile;
import com.orgmemory.core.assistant.AssistantFileProcessingService;
import com.orgmemory.core.knowledge.retrieval.EmbeddingDistanceMetric;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRegistry;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileSpec;
import com.orgmemory.core.knowledge.sourceledger.DocumentProcessingProfileSnapshot;
import com.orgmemory.core.knowledge.sourceledger.ProcessingProfileMismatchException;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.graphrag.parsing.DocumentParseRequest;
import com.orgmemory.graphrag.parsing.DocumentParseException;
import com.orgmemory.worker.WorkProcessingResult;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class AssistantFileProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AssistantFileProcessor.class);
    private static final TokenCountBatchingStrategy BATCHING = new TokenCountBatchingStrategy();

    private final ObjectProvider<AssistantFileProcessingService> files;
    private final ObjectStoragePort objects;
    private final ObjectProvider<EmbeddingModel> embeddingModels;
    private final EmbeddingProfileRegistry embeddingProfiles;
    private final AiRouteResolver aiRoutes;
    private final SourceProcessingProperties properties;
    private final DocumentProcessingEngine processing;

    AssistantFileProcessor(
            ObjectProvider<AssistantFileProcessingService> files,
            ObjectStoragePort objects,
            ObjectProvider<EmbeddingModel> embeddingModels,
            EmbeddingProfileRegistry embeddingProfiles,
            AiRouteResolver aiRoutes,
            SourceProcessingProperties properties,
            DocumentProcessingEngine processing) {
        this.files = files;
        this.objects = objects;
        this.embeddingModels = embeddingModels;
        this.embeddingProfiles = embeddingProfiles;
        this.aiRoutes = aiRoutes;
        this.properties = properties;
        this.processing = processing;
    }

    WorkProcessingResult processNext() {
        AssistantFileProcessingService processingService = files.getIfAvailable();
        if (processingService == null) return WorkProcessingResult.EMPTY_OR_DEFERRED;
        AssistantFileProcessingProfile requested = profile(processing.requestedProcessingProfile());
        Optional<AssistantFileProcessingClaim> claimed = processingService.claimNext(
                properties.workerId(), properties.leaseDuration(), requested);
        if (claimed.isEmpty()) return WorkProcessingResult.EMPTY_OR_DEFERRED;
        process(processingService, claimed.get());
        return WorkProcessingResult.PROCESSED;
    }

    WorkProcessingResult cleanupNext() {
        AssistantFileProcessingService processingService = files.getIfAvailable();
        if (processingService == null) return WorkProcessingResult.EMPTY_OR_DEFERRED;
        return processingService.claimCleanup().map(claim -> {
            try {
                objects.delete(new ObjectKey(claim.objectKey()));
                processingService.completeCleanup(claim.fileId());
                return WorkProcessingResult.PROCESSED;
            } catch (RuntimeException failure) {
                LOG.warn("Assistant file cleanup {} will be retried", claim.fileId(), failure);
                return WorkProcessingResult.EMPTY_OR_DEFERRED;
            }
        }).orElse(WorkProcessingResult.EMPTY_OR_DEFERRED);
    }

    private void process(
            AssistantFileProcessingService processingService,
            AssistantFileProcessingClaim claim) {
        try {
            RequestedProcessingPolicy policy = processing.requestedPolicy(snapshot(claim.requestedProfile()));
            var embeddingRoute = aiRoutes.resolve(AiWorkload.DOCUMENT_EMBEDDING);
            if (!embeddingRoute.modelId().equals(policy.embeddingModel())) {
                throw new IllegalStateException("Document embedding route changed from the pinned profile");
            }
            EmbeddingModel embeddingModel = embeddingModels.getIfAvailable();
            if (embeddingModel == null) {
                fail(processingService, claim, "EMBEDDING_MODEL_UNAVAILABLE", true);
                return;
            }
            byte[] bytes;
            try (var object = objects.open(new ObjectKey(claim.objectKey()))) {
                bytes = object.stream().readAllBytes();
            }
            verifyObject(claim, bytes);
            ProcessedSourceDocument processed = processing.process(
                    new DocumentParseRequest(
                            claim.fileName(), claim.mediaType(), bytes, Optional.empty()),
                    embeddingModel,
                    snapshot(claim.requestedProfile()),
                    claim.resolvedProfile().map(AssistantFileProcessor::snapshot));
            AssistantFileProcessingProfile resolvedProfile = profile(
                    new DocumentProcessingProfileSnapshot(
                            processed.profile().canonicalForm(),
                            processed.profile().profileSha256()));
            processingService.bindResolvedProfile(
                    claim.fileId(), properties.workerId(), resolvedProfile);
            EmbeddingProfileRef embeddingProfile = embeddingProfiles.resolve(
                    claim.organizationId(),
                    new EmbeddingProfileSpec(
                            policy.embeddingProvider(),
                            policy.embeddingModel(),
                            policy.embeddingDimensions(),
                            EmbeddingDistanceMetric.COSINE));
            List<float[]> vectors = embeddingModel.embed(
                    processed.chunks().stream()
                            .map(chunk -> new Document(chunk.content()))
                            .toList(),
                    null,
                    BATCHING);
            if (vectors.size() != processed.chunks().size()) {
                throw new IllegalStateException("Embedding result count does not match chunk count");
            }
            var drafts = new java.util.ArrayList<AssistantFileChunkDraft>(vectors.size());
            for (int index = 0; index < vectors.size(); index++) {
                var chunk = processed.chunks().get(index);
                drafts.add(new AssistantFileChunkDraft(
                        chunk.content(),
                        chunk.heading(),
                        chunk.provenance().startPage(),
                        chunk.provenance().endPage(),
                        chunk.tokenCount(),
                        chunk.provenance().startChar(),
                        chunk.provenance().endChar(),
                        chunk.provenance().blockIndexes(),
                        chunk.provenance().canonicalTextSha256(),
                        vectors.get(index)));
            }
            processingService.complete(
                    claim.fileId(),
                    properties.workerId(),
                    resolvedProfile,
                    embeddingProfile.id(),
                    embeddingProfile.dimensions(),
                    drafts);
            LOG.info("Assistant file {} is ready with {} chunks", claim.fileId(), drafts.size());
        } catch (ProcessingProfileMismatchException mismatch) {
            fail(processingService, claim, "PROCESSING_PROFILE_MISMATCH", false);
        } catch (DocumentParseException rejected) {
            fail(processingService, claim, rejected.code(), false);
        } catch (RejectedSourceException rejected) {
            fail(processingService, claim, rejected.code(), false);
        } catch (IOException unreadable) {
            fail(processingService, claim, "OBJECT_READ_FAILED", true);
        } catch (RuntimeException failure) {
            LOG.warn("Assistant file {} processing failed", claim.fileId(), failure);
            fail(processingService, claim, "PROCESSING_FAILED", true);
        }
    }

    private void fail(
            AssistantFileProcessingService processingService,
            AssistantFileProcessingClaim claim,
            String code,
            boolean retryable) {
        try {
            processingService.fail(claim.fileId(), properties.workerId(), code, retryable);
        } catch (IllegalStateException staleClaim) {
            LOG.info(
                    "Assistant file {} was denied while its worker claim was active",
                    claim.fileId());
        }
    }

    private static void verifyObject(AssistantFileProcessingClaim claim, byte[] bytes) {
        if (bytes.length != claim.contentLength()) {
            throw new RejectedSourceException(
                    "CONTENT_LENGTH_MISMATCH", "Stored Assistant file length changed");
        }
        try {
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!actual.equals(claim.contentSha256())) {
                throw new RejectedSourceException(
                        "CONTENT_HASH_MISMATCH", "Stored Assistant file digest changed");
            }
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static AssistantFileProcessingProfile profile(DocumentProcessingProfileSnapshot snapshot) {
        return new AssistantFileProcessingProfile(snapshot.canonicalForm(), snapshot.sha256());
    }

    private static DocumentProcessingProfileSnapshot snapshot(AssistantFileProcessingProfile profile) {
        return new DocumentProcessingProfileSnapshot(profile.canonicalForm(), profile.sha256());
    }
}
