package com.orgmemory.worker.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.core.knowledge.KnowledgeTextDocument;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringAiKnowledgeTextChunkerTests {

    @Test
    void rejectsInputThatExceedsTheApplicationChunkLimit() {
        var chunker = new SpringAiKnowledgeTextChunker(properties(1));
        String oversized = "OrgMemory knowledge policy and evidence. ".repeat(200);

        RejectedSourceException failure = assertThrows(
                RejectedSourceException.class,
                () -> chunker.split(List.of(new KnowledgeTextDocument(
                        oversized, null, null))));

        assertEquals("CHUNK_LIMIT_EXCEEDED", failure.code());
    }

    @Test
    void preservesAnInputWithinTheApplicationChunkLimit() {
        var chunker = new SpringAiKnowledgeTextChunker(properties(2));

        var chunks = chunker.split(List.of(
                new KnowledgeTextDocument("A short policy statement.", 1, 1)));

        assertEquals(1, chunks.size());
        assertEquals("A short policy statement.", chunks.getFirst().content());
    }

    private static SourceProcessingProperties properties(int maximumChunks) {
        return new SourceProcessingProperties(
                false,
                Duration.ofSeconds(1),
                "test-worker",
                Duration.ofMinutes(1),
                "pipeline-v1",
                "parser-v1",
                "chunker-v1",
                "normalizer-v1",
                "openai",
                "text-embedding-3-large",
                3,
                8,
                maximumChunks);
    }
}
