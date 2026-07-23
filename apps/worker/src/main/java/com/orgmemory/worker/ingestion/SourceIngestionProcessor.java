package com.orgmemory.worker.ingestion;

import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.knowledge.AclCaptureStatus;
import com.orgmemory.core.knowledge.ClaimedSourceRevision;
import com.orgmemory.core.knowledge.EmbeddingDistanceMetric;
import com.orgmemory.core.knowledge.EmbeddingProfileRef;
import com.orgmemory.core.knowledge.EmbeddingProfileRegistry;
import com.orgmemory.core.knowledge.EmbeddingProfileSpec;
import com.orgmemory.core.knowledge.KnowledgeAssetRef;
import com.orgmemory.core.knowledge.KnowledgeChunkDraftAssembler;
import com.orgmemory.core.knowledge.KnowledgeTextChunk;
import com.orgmemory.core.knowledge.KnowledgeTextChunker;
import com.orgmemory.core.knowledge.KnowledgeTextDocument;
import com.orgmemory.core.knowledge.KnowledgeAssetPublicationService;
import com.orgmemory.core.knowledge.KnowledgeIngestionService;
import com.orgmemory.core.knowledge.NormalizeRawSourceCommand;
import com.orgmemory.core.knowledge.NormalizedRecordRef;
import com.orgmemory.core.knowledge.PublishKnowledgeAssetCommand;
import com.orgmemory.core.knowledge.RawSourceRef;
import com.orgmemory.core.knowledge.RegisterRawSourceCommand;
import com.orgmemory.core.knowledge.SourceAclEntryCommand;
import com.orgmemory.core.knowledge.SourceIngestionCoordinator;
import com.orgmemory.core.knowledge.SourcePrincipalType;
import com.orgmemory.core.knowledge.SourceRevisionStatus;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.permission.AccessGate;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class SourceIngestionProcessor {

    private static final Logger log = LoggerFactory.getLogger(SourceIngestionProcessor.class);

    private final SourceIngestionCoordinator coordinator;
    private final KnowledgeIngestionService ingestion;
    private final KnowledgeAssetPublicationService publications;
    private final EmbeddingProfileRegistry embeddingProfiles;
    private final ObjectStoragePort objects;
    private final ObjectProvider<EmbeddingModel> embeddingModels;
    private final AiRouteResolver aiRoutes;
    private final SourceDocumentReader reader;
    private final SourceProcessingProperties properties;
    private final KnowledgeTextChunker chunker;

    SourceIngestionProcessor(
            SourceIngestionCoordinator coordinator,
            KnowledgeIngestionService ingestion,
            KnowledgeAssetPublicationService publications,
            EmbeddingProfileRegistry embeddingProfiles,
            ObjectStoragePort objects,
            ObjectProvider<EmbeddingModel> embeddingModels,
            AiRouteResolver aiRoutes,
            SourceDocumentReader reader,
            SourceProcessingProperties properties,
            KnowledgeTextChunker chunker) {
        this.coordinator = coordinator;
        this.ingestion = ingestion;
        this.publications = publications;
        this.embeddingProfiles = embeddingProfiles;
        this.objects = objects;
        this.embeddingModels = embeddingModels;
        this.aiRoutes = aiRoutes;
        this.reader = reader;
        this.properties = properties;
        this.chunker = chunker;
    }

    void processNext() {
        coordinator.claimNext(properties.workerId(), properties.leaseDuration())
                .ifPresent(this::process);
    }

    private void process(ClaimedSourceRevision claim) {
        String failureStage = "VALIDATION";
        Path temporaryFile = null;
        try {
            var embeddingRoute = aiRoutes.resolve(AiWorkload.DOCUMENT_EMBEDDING);
            if (!embeddingRoute.modelId().equals(properties.embeddingModel())) {
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
            ParsedSource parsed = reader.read(temporaryFile, claim.fileName());
            RawSourceRef raw = registerRawSource(claim, parsed);
            NormalizedRecordRef normalized = ingestion.normalize(new NormalizeRawSourceCommand(
                    claim.organizationId(),
                    raw.rawSourceObjectId(),
                    properties.normalizerVersion(),
                    claim.fileName(),
                    parsed.normalizedText(),
                    "und"));

            coordinator.markStage(
                    claim.jobId(), properties.workerId(), SourceRevisionStatus.CHUNKING, properties.leaseDuration());
            failureStage = "CHUNKING";
            List<KnowledgeTextChunk> candidates = split(parsed.documents());

            coordinator.markStage(
                    claim.jobId(), properties.workerId(), SourceRevisionStatus.EMBEDDING, properties.leaseDuration());
            failureStage = "EMBEDDING";
            EmbeddingProfileRef embeddingProfile = embeddingProfiles.resolve(
                    claim.organizationId(),
                    new EmbeddingProfileSpec(
                            properties.embeddingProvider(),
                            properties.embeddingModel(),
                            properties.embeddingDimensions(),
                            EmbeddingDistanceMetric.COSINE));
            List<Document> embeddingDocuments = candidates.stream()
                    .map(candidate -> new Document(candidate.content()))
                    .toList();
            List<float[]> vectors = embeddingModel.embed(
                    embeddingDocuments, null, new TokenCountBatchingStrategy());
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
                    embeddingProfile,
                    properties.pipelineVersion(),
                    drafts));
            coordinator.complete(
                    claim.jobId(),
                    properties.workerId(),
                    properties.pipelineVersion(),
                    properties.parserVersion(),
                    properties.chunkerVersion(),
                    embeddingProfile,
                    raw,
                    normalized,
                    asset);
            log.info(
                    "Source revision {} is ready with {} chunks on attempt {}",
                    claim.sourceRevisionId(),
                    drafts.size(),
                    claim.attempt());
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

    private RawSourceRef registerRawSource(ClaimedSourceRevision claim, ParsedSource parsed) {
        return ingestion.registerRawSource(new RegisterRawSourceCommand(
                claim.organizationId(),
                claim.departmentId(),
                "UPLOAD",
                claim.sourceConnectionKey(),
                claim.externalObjectId(),
                "1",
                parsed.detectedMediaType(),
                claim.fileName(),
                parsed.normalizedText(),
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

    private List<KnowledgeTextChunk> split(List<Document> documents) {
        return chunker.split(documents.stream()
                .map(document -> new KnowledgeTextDocument(
                        document.getText(),
                        number(document, PagePdfDocumentReader.METADATA_START_PAGE_NUMBER),
                        number(document, PagePdfDocumentReader.METADATA_END_PAGE_NUMBER)))
                .toList());
    }

    private static Integer number(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value instanceof Number number ? number.intValue() : null;
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
