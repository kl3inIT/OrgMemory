package com.orgmemory.graphrag.chunking;

import com.orgmemory.graphrag.parsing.CanonicalDocument;
import java.util.Objects;
import java.util.Optional;

public record ChunkingRequest(
        CanonicalDocument document,
        TextTokenizer tokenizer,
        Optional<TextEmbeddingPort> semanticEmbedding) {

    public ChunkingRequest {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(tokenizer, "tokenizer");
        semanticEmbedding = Objects.requireNonNull(semanticEmbedding, "semanticEmbedding");
    }
}
