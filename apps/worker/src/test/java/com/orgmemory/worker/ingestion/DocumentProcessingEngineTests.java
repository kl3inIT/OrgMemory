package com.orgmemory.worker.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        var engine = new DocumentProcessingEngine(properties, new SourceDocumentReader());
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
