package com.orgmemory.graphrag.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.chunking.ChunkTokenLimitExceededException;
import com.orgmemory.graphrag.chunking.ChunkProvenance;
import com.orgmemory.graphrag.chunking.ChunkedText;
import com.orgmemory.graphrag.chunking.ChunkingRequest;
import com.orgmemory.graphrag.chunking.FixedTokenChunker;
import com.orgmemory.graphrag.chunking.FixedTokenOptions;
import com.orgmemory.graphrag.chunking.ParagraphSemanticChunker;
import com.orgmemory.graphrag.chunking.ParagraphSemanticOptions;
import com.orgmemory.graphrag.chunking.RecursiveCharacterChunker;
import com.orgmemory.graphrag.chunking.RecursiveCharacterOptions;
import com.orgmemory.graphrag.chunking.SemanticVectorChunker;
import com.orgmemory.graphrag.chunking.SemanticVectorOptions;
import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.parsing.DocumentBlock;
import com.orgmemory.graphrag.parsing.DocumentBlockKind;
import com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Normalized golden behavior captured from the pinned LightRAG v1.5.4 strategy contracts.
 *
 * <p>The test tokenizer is one Unicode code point per token, matching upstream's offline
 * character-tokenizer fixtures while also exercising surrogate-pair offsets.
 */
class LightRagChunkerGoldenTests {

    private final CodePointTokenizer tokenizer = new CodePointTokenizer();

    @Test
    void fixedTokenMatchesUpstreamWindowsAndExactSpans() throws IOException {
        Properties fixture = new Properties();
        try (var stream = Objects.requireNonNull(
                LightRagChunkerGoldenTests.class.getResourceAsStream(
                        "/lightrag-v1.5.4/fixed-token-golden.properties"))) {
            fixture.load(stream);
        }
        CanonicalDocument document = CanonicalDocument.text(fixture.getProperty("source"));
        var chunks = new FixedTokenChunker().chunk(
                request(document),
                new FixedTokenOptions(
                        Integer.parseInt(fixture.getProperty("chunkSize")),
                        Integer.parseInt(fixture.getProperty("overlap")),
                        null,
                        false));

        assertEquals(List.of(fixture.getProperty("chunks").split("\\|")),
                chunks.stream().map(chunk -> chunk.content()).toList());
        assertEquals(List.of(fixture.getProperty("spans").split("\\|")),
                chunks.stream()
                        .map(chunk -> chunk.provenance().startChar()
                                + ":"
                                + chunk.provenance().endChar())
                        .toList());
    }

    @Test
    void fixedTokenDelimiterOnlyRejectsOversizedSourceSegment() {
        CanonicalDocument document = CanonicalDocument.text("small|oversized");
        assertThrows(
                ChunkTokenLimitExceededException.class,
                () -> new FixedTokenChunker().chunk(
                        request(document),
                        new FixedTokenOptions(5, 0, "|", true)));
    }

    @Test
    void fixedTokenPreservesUnicodeSourceOffsets() {
        CanonicalDocument document = CanonicalDocument.text("A😀BC");
        var chunks = new FixedTokenChunker().chunk(
                request(document),
                new FixedTokenOptions(2, 0, null, false));

        assertEquals(List.of("A😀", "BC"),
                chunks.stream().map(chunk -> chunk.content()).toList());
        assertEquals(3, chunks.getFirst().provenance().endChar());
    }

    @Test
    void chunkValueDoesNotMutateCanonicalBoundaryWhitespace() {
        var chunk = new ChunkedText(
                0,
                " padded ",
                2,
                null,
                new ChunkProvenance(
                        0,
                        8,
                        null,
                        null,
                        List.of(0),
                        "0".repeat(64)));

        assertEquals(" padded ", chunk.content());
    }

    @Test
    void canonicalDocumentsRejectCarriageReturns() {
        String content = "first\r\nsecond";
        assertThrows(
                IllegalArgumentException.class,
                () -> new CanonicalDocument(
                        content,
                        ResolvedDocumentProcessingProfile.sha256(content),
                        List.of(new DocumentBlock(
                                0,
                                DocumentBlockKind.PARAGRAPH,
                                0,
                                content.length(),
                                null,
                                null,
                                null,
                                Map.of()))));
    }

    @Test
    void recursiveCharacterPrefersParagraphBoundariesAndHonorsCap() {
        CanonicalDocument document =
                CanonicalDocument.text("Alpha one.\n\nBeta two.\n\nGamma three.");
        var chunks = new RecursiveCharacterChunker().chunk(
                request(document),
                new RecursiveCharacterOptions(20, 2, List.of("\n\n", " ", ""), true));

        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.tokenCount() <= 20));
        assertTrue(chunks.stream().allMatch(chunk ->
                document.content()
                        .substring(
                                chunk.provenance().startChar(),
                                chunk.provenance().endChar())
                        .equals(chunk.content())));
    }

    @Test
    void semanticVectorBreaksAtTheLargestMeaningShift() {
        CanonicalDocument document =
                CanonicalDocument.text("Cats purr. Kittens sleep. Rockets launch. Orbits decay.");
        var embedding = new DeterministicEmbeddingPort(text -> {
            if (text.startsWith("Cats")) {
                return new float[] {1, 0};
            }
            if (text.startsWith("Kittens")) {
                return new float[] {0.9f, 0.1f};
            }
            if (text.startsWith("Rockets")) {
                return new float[] {-1, 0};
            }
            return new float[] {-0.9f, 0.1f};
        });
        var chunks = new SemanticVectorChunker().chunk(
                new ChunkingRequest(document, tokenizer, Optional.of(embedding)),
                new SemanticVectorOptions(
                        200,
                        0,
                        SemanticVectorOptions.BreakpointThreshold.PERCENTILE,
                        50,
                        SemanticVectorOptions.DEFAULT_SENTENCE_SPLIT_REGEX));

        assertEquals(
                List.of("Cats purr. Kittens sleep.", "Rockets launch. Orbits decay."),
                chunks.stream().map(chunk -> chunk.content()).toList());
    }

    @Test
    void paragraphSemanticCarriesHeadingAndSplitsOversizedTableByRows() {
        String content = "Benefits\nName | Value\nLeave | 12\nRemote | Yes\nTraining | Included";
        int headingEnd = content.indexOf('\n');
        int tableStart = headingEnd + 1;
        CanonicalDocument document = new CanonicalDocument(
                content,
                ResolvedDocumentProcessingProfile.sha256(content),
                List.of(
                        new DocumentBlock(
                                0,
                                DocumentBlockKind.HEADING,
                                0,
                                headingEnd,
                                1,
                                1,
                                1,
                                Map.of("structured", "true")),
                        new DocumentBlock(
                                1,
                                DocumentBlockKind.TABLE,
                                tableStart,
                                content.length(),
                                1,
                                1,
                                null,
                                Map.of("structured", "true"))));

        var chunks = new ParagraphSemanticChunker().chunk(
                request(document),
                new ParagraphSemanticOptions(
                        25,
                        0,
                        100,
                        false,
                        List.of("references", "参考文献")));

        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.heading().startsWith("Benefits")));
        assertTrue(chunks.getFirst().heading().endsWith("[part 1]"));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.content().startsWith("Name | Value")));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.tokenCount() <= 25));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.provenance().startPage() == 1));
    }

    @Test
    void paragraphSemanticUsesShortParagraphsAsBalancedAnchors() {
        String body = "A".repeat(20)
                + "\n\nCheckpoint"
                + "\n\n"
                + "B".repeat(20)
                + "\n\nNext"
                + "\n\n"
                + "C".repeat(20);
        String content = "Guide\n" + body;
        int bodyStart = content.indexOf('\n') + 1;
        CanonicalDocument document = new CanonicalDocument(
                content,
                ResolvedDocumentProcessingProfile.sha256(content),
                List.of(
                        new DocumentBlock(
                                0,
                                DocumentBlockKind.HEADING,
                                0,
                                bodyStart - 1,
                                1,
                                1,
                                1,
                                Map.of("structured", "true")),
                        new DocumentBlock(
                                1,
                                DocumentBlockKind.PARAGRAPH,
                                bodyStart,
                                content.length(),
                                1,
                                1,
                                null,
                                Map.of("structured", "true"))));

        var chunks = new ParagraphSemanticChunker().chunk(
                request(document),
                new ParagraphSemanticOptions(
                        35,
                        0,
                        12,
                        false,
                        List.of("references")));

        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.tokenCount() <= 35));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.heading().contains("Checkpoint")));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.heading().contains("[part ")));
    }

    private ChunkingRequest request(CanonicalDocument document) {
        return new ChunkingRequest(document, tokenizer, Optional.empty());
    }
}
