package com.orgmemory.worker.ingestion;

import com.orgmemory.core.knowledge.KnowledgeTextChunk;
import com.orgmemory.core.knowledge.KnowledgeTextChunker;
import com.orgmemory.core.knowledge.KnowledgeTextDocument;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

@Component
class SpringAiKnowledgeTextChunker implements KnowledgeTextChunker {

    private final SourceProcessingProperties properties;
    private final TokenTextSplitter splitter;

    SpringAiKnowledgeTextChunker(SourceProcessingProperties properties) {
        this.properties = properties;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSize())
                .withMaxNumChunks(properties.maximumChunks())
                .build();
    }

    @Override
    public String version() {
        return properties.chunkerVersion();
    }

    @Override
    public List<KnowledgeTextChunk> split(List<KnowledgeTextDocument> documents) {
        List<KnowledgeTextChunk> chunks = new ArrayList<>();
        for (KnowledgeTextDocument source : documents) {
            Document document = new Document(source.content());
            for (Document piece : splitter.apply(List.of(document))) {
                if (piece.getText() == null || piece.getText().isBlank()) {
                    continue;
                }
                chunks.add(new KnowledgeTextChunk(
                        piece.getText().strip(), source.startPage(), source.endPage()));
                if (chunks.size() > properties.maximumChunks()) {
                    throw new RejectedSourceException(
                            "CHUNK_LIMIT_EXCEEDED",
                            "The document exceeds the configured chunk limit");
                }
            }
        }
        if (chunks.isEmpty()) {
            throw new RejectedSourceException(
                    "NO_EXTRACTABLE_TEXT", "No extractable text was found");
        }
        return List.copyOf(chunks);
    }
}
