package com.orgmemory.worker.ingestion;

import com.orgmemory.graphrag.chunking.ChunkedText;
import com.orgmemory.graphrag.parsing.DocumentParseResult;
import com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile;
import java.util.List;
import java.util.Objects;

record ProcessedSourceDocument(
        DocumentParseResult parseResult,
        List<ChunkedText> chunks,
        ResolvedDocumentProcessingProfile profile) {

    ProcessedSourceDocument {
        Objects.requireNonNull(parseResult, "parseResult");
        chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
        if (chunks.isEmpty()) {
            throw new RejectedSourceException(
                    "NO_EXTRACTABLE_CHUNKS",
                    "Processed document requires at least one chunk");
        }
        Objects.requireNonNull(profile, "profile");
    }
}
