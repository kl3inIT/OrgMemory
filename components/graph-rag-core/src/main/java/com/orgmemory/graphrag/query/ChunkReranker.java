package com.orgmemory.graphrag.query;

import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Optional provider effect for query/chunk cross-encoder reranking. */
public interface ChunkReranker {

    ProcessingComponentRef component();

    List<Score> rerank(String query, List<Candidate> candidates, int limit);

    record Candidate(UUID chunkId, String content) {
        public Candidate {
            Objects.requireNonNull(chunkId, "chunkId");
            Objects.requireNonNull(content, "content");
        }
    }

    record Score(UUID chunkId, double relevance) {
        public Score {
            Objects.requireNonNull(chunkId, "chunkId");
            if (!Double.isFinite(relevance)) {
                throw new IllegalArgumentException("relevance must be finite");
            }
        }
    }
}
