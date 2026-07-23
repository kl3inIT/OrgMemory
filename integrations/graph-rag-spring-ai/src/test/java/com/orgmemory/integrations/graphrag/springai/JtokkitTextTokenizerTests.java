package com.orgmemory.integrations.graphrag.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orgmemory.graphrag.chunking.FixedTokenChunker;
import com.orgmemory.graphrag.chunking.FixedTokenOptions;
import com.orgmemory.graphrag.chunking.ChunkingRequest;
import com.orgmemory.graphrag.parsing.CanonicalDocument;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JtokkitTextTokenizerTests {

    @Test
    void fixedWindowsMapBackToExactRepeatedSourceText() {
        var tokenizer = new JtokkitTextTokenizer("o200k_base");
        var document = CanonicalDocument.text(
                "Repeated phrase. Repeated phrase. Tiếng Việt chính xác. ".repeat(20));

        var chunks = new FixedTokenChunker().chunk(
                new ChunkingRequest(document, tokenizer, Optional.empty()),
                new FixedTokenOptions(24, 6, null, false));

        for (var chunk : chunks) {
            assertEquals(
                    chunk.content(),
                    document.content().substring(
                            chunk.provenance().startChar(),
                            chunk.provenance().endChar()));
        }
    }
}
