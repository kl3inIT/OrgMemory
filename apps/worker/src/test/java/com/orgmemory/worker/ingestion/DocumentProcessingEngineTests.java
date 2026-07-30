package com.orgmemory.worker.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.parsing.DocumentParseRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

class DocumentProcessingEngineTests {

    @Test
    void rejectsDefiniteChunkOverflowBeforeCallingSemanticEmbedding() {
        var properties = new SourceProcessingProperties(
                false,
                Duration.ofSeconds(1),
                "test-worker",
                Duration.ofMinutes(1),
                "test-pipeline",
                "passthrough",
                "semantic-vector",
                "o200k_base",
                "normalizer",
                "fixture",
                "fixture-model",
                2,
                2,
                0,
                2,
                1);
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
                properties("passthrough", "fixed-token"),
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

    private static SourceProcessingProperties properties(
            String parserId,
            String chunkerId) {
        return new SourceProcessingProperties(
                false,
                Duration.ofSeconds(1),
                "test-worker",
                Duration.ofMinutes(1),
                "test-pipeline",
                parserId,
                chunkerId,
                "o200k_base",
                "normalizer",
                "fixture",
                "fixture-model",
                64,
                8,
                0,
                64,
                1);
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
}
