package com.orgmemory.worker.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.knowledge.sourceledger.DocumentProcessingProfileSnapshot;
import com.orgmemory.core.knowledge.sourceledger.ProcessingProfileMismatchException;
import com.orgmemory.graphrag.chunking.RecursiveCharacterChunker;
import com.orgmemory.graphrag.chunking.SemanticVectorChunker;
import com.orgmemory.graphrag.parsing.DocumentParseRequest;
import com.orgmemory.integrations.documentparsing.springai.SpringAiDocumentParser;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

class DocumentProcessingEngineTests {

    @Test
    void rejectsDefiniteChunkOverflowBeforeCallingSemanticEmbedding() {
        var properties = new SourceProcessingProperties(
                "test-worker",
                Duration.ofMinutes(1),
                "test-pipeline",
                "passthrough",
                RequestedProcessingPolicy.SEMANTIC_VECTOR_V1,
                "o200k_base",
                "normalizer",
                "fixture",
                "fixture-model",
                2,
                2,
                0,
                2,
                1,
                null,
                null,
                null);
        var engine = new DocumentProcessingEngine(properties, new SpringAiDocumentParser());
        var request = new DocumentParseRequest(
                "large.txt",
                "text/plain",
                "one two three four five six".getBytes(StandardCharsets.UTF_8),
                Optional.empty());

        RejectedSourceException failure = assertThrows(
                RejectedSourceException.class,
                () -> engine.process(request, new FailingEmbeddingModel()));

        assertEquals("CHUNK_LIMIT_EXCEEDED", failure.code());
    }

    /**
     * The caller makes one {@code process} call and cannot see where parsing ended, so the two
     * measurements have to leave the engine separately or the pair collapses into one number
     * that hides which half a slow document spent its time in.
     *
     * <p>This asserts the counts the two stage events report, not the accuracy of the timing.
     * A timing assertion was tried and removed: on a fixture this small, starting the chunk
     * clock before parsing still passed every bound the test could state, so it proved nothing
     * it claimed to. The stage events themselves are proven end to end in
     * {@code SourceIngestionPipelineIntegrationTests}.
     */
    @Test
    void carriesTheParseAndChunkMeasurementsOutSeparately() {
        var engine = new DocumentProcessingEngine(
                properties("passthrough", RequestedProcessingPolicy.FIXED_TOKEN_V1),
                new SpringAiDocumentParser());

        ProcessedSourceDocument processed = engine.process(
                new DocumentParseRequest(
                        "notes.txt",
                        "text/plain",
                        "one two three four five six seven eight"
                                .getBytes(StandardCharsets.UTF_8),
                        Optional.empty()),
                new FailingEmbeddingModel());

        assertFalse(
                processed.parseResult().document().blocks().isEmpty(),
                "PARSE reports blocks produced, so an empty list would make it report zero work");
        assertFalse(
                processed.chunks().isEmpty(),
                "CHUNK reports chunks produced");
    }

    @Test
    void runsAMixedWordDocumentThroughTheStructuredBlockPolicy() throws Exception {
        var engine = new DocumentProcessingEngine(
                properties("spring-ai-document-reader", null),
                new SpringAiDocumentParser());

        ProcessedSourceDocument processed = engine.process(
                new DocumentParseRequest(
                        "leave-policy.docx",
                        "application/octet-stream",
                        wordPolicy(),
                        Optional.empty()),
                new FailingEmbeddingModel());

        assertEquals("paragraph-semantic", processed.profile().requestedChunker().id());
        assertEquals(
                RequestedProcessingPolicy.STRUCTURED_BLOCK_V1,
                processed.profile().options().get("request.policy.id"));
        assertTrue(processed.chunks().stream()
                .anyMatch(chunk -> chunk.content().startsWith("Cấp\tSố ngày")));
        assertTrue(processed.profile().options().containsKey("chunkManifest.sha256"));
    }

    @Test
    void retryUsesThePinnedRecursiveFallbackWithoutCallingSemanticEmbeddingAgain() {
        var engine = new DocumentProcessingEngine(
                properties("passthrough", RequestedProcessingPolicy.SEMANTIC_VECTOR_V1),
                new SpringAiDocumentParser());
        DocumentParseRequest request = new DocumentParseRequest(
                "notes.txt",
                "text/plain",
                "Alpha has context. Beta changes topic. Gamma closes the note."
                        .getBytes(StandardCharsets.UTF_8),
                Optional.empty());
        DocumentProcessingProfileSnapshot requested = engine.requestedProcessingProfile();

        ProcessedSourceDocument first = engine.process(
                request, new UnavailableEmbeddingModel(), requested, Optional.empty());
        DocumentProcessingProfileSnapshot resolved = new DocumentProcessingProfileSnapshot(
                first.profile().canonicalForm(), first.profile().profileSha256());

        ProcessedSourceDocument retry = engine.process(
                request, new FailingEmbeddingModel(), requested, Optional.of(resolved));

        assertEquals(SemanticVectorChunker.COMPONENT, retry.profile().requestedChunker());
        assertEquals(RecursiveCharacterChunker.COMPONENT, retry.profile().actualChunker());
        assertEquals(first.profile().profileSha256(), retry.profile().profileSha256());
        assertEquals(
                "SEMANTIC_EMBEDDING_UNAVAILABLE",
                retry.profile().options().get("fallback.code"));
    }

    @Test
    void retryRejectsContentThatNoLongerMatchesThePinnedChunkManifest() {
        var engine = new DocumentProcessingEngine(
                properties("passthrough", RequestedProcessingPolicy.FIXED_TOKEN_V1),
                new SpringAiDocumentParser());
        DocumentProcessingProfileSnapshot requested = engine.requestedProcessingProfile();
        ProcessedSourceDocument first = engine.process(
                textRequest("Original evidence remains immutable."),
                new FailingEmbeddingModel(),
                requested,
                Optional.empty());
        DocumentProcessingProfileSnapshot resolved = new DocumentProcessingProfileSnapshot(
                first.profile().canonicalForm(), first.profile().profileSha256());

        assertThrows(
                ProcessingProfileMismatchException.class,
                () -> engine.process(
                        textRequest("Changed evidence must not reuse the old manifest."),
                        new FailingEmbeddingModel(),
                        requested,
                        Optional.of(resolved)));
    }

    private static SourceProcessingProperties properties(
            String parserId,
            String policyId) {
        return new SourceProcessingProperties(
                "test-worker",
                Duration.ofMinutes(1),
                "test-pipeline",
                parserId,
                policyId,
                "o200k_base",
                "normalizer",
                "fixture",
                "fixture-model",
                64,
                8,
                0,
                64,
                64,
                null,
                null,
                null);
    }

    private static DocumentParseRequest textRequest(String content) {
        return new DocumentParseRequest(
                "notes.txt",
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8),
                Optional.empty());
    }

    private static byte[] wordPolicy() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Chính sách nghỉ phép.");
            var table = document.createTable(3, 2);
            String[][] cells = {
                {"Cấp", "Số ngày"},
                {"Nhân viên", "12"},
                {"Quản lý", "15"}
            };
            for (int row = 0; row < cells.length; row++) {
                for (int column = 0; column < cells[row].length; column++) {
                    table.getRow(row).getCell(column).setText(cells[row][column]);
                }
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    private static final class FailingEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            throw new AssertionError("embedding provider must not be called");
        }

        @Override
        public float[] embed(Document document) {
            throw new AssertionError("embedding provider must not be called");
        }
    }

    private static final class UnavailableEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            throw new IllegalStateException("embedding provider is unavailable");
        }

        @Override
        public float[] embed(Document document) {
            throw new IllegalStateException("embedding provider is unavailable");
        }
    }
}
