package com.orgmemory.worker.ingestion;

import com.orgmemory.graphrag.chunking.ChunkedText;
import com.orgmemory.graphrag.parsing.DocumentParseResult;
import com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * @param parseDuration how long the routed parser took
 * @param chunkDuration how long chunking took, including a semantic chunker's failover to the
 *                      recursive one
 *
 *     <p>Both are carried out of the engine rather than timed by the caller, because the caller
 *     makes one {@code process} call and cannot see where inside it parsing ended. Reporting the
 *     pair as one duration would hide which half a slow document spent its time in, and the two
 *     have unrelated causes — a parser handed a large scanned file, against a chunker falling
 *     back to a different algorithm.
 */
record ProcessedSourceDocument(
        DocumentParseResult parseResult,
        List<ChunkedText> chunks,
        ResolvedDocumentProcessingProfile profile,
        Duration parseDuration,
        Duration chunkDuration) {

    ProcessedSourceDocument {
        Objects.requireNonNull(parseResult, "parseResult");
        chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
        if (chunks.isEmpty()) {
            throw new RejectedSourceException(
                    "NO_EXTRACTABLE_CHUNKS",
                    "Processed document requires at least one chunk");
        }
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(parseDuration, "parseDuration");
        Objects.requireNonNull(chunkDuration, "chunkDuration");
        if (parseDuration.isNegative() || chunkDuration.isNegative()) {
            throw new IllegalArgumentException("durations must not be negative");
        }
    }
}
