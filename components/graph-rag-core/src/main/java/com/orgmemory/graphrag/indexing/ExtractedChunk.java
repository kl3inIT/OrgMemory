package com.orgmemory.graphrag.indexing;

import com.orgmemory.graphrag.model.ExtractionResult;
import java.util.Objects;
import java.util.UUID;

public record ExtractedChunk(UUID chunkId, ExtractionResult result) {

    public ExtractedChunk {
        Objects.requireNonNull(chunkId, "chunkId");
        Objects.requireNonNull(result, "result");
    }
}
