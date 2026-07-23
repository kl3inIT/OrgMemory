package com.orgmemory.core.knowledge;

import java.util.List;

/** Shared chunking boundary used by upload and connector ingestion. */
public interface KnowledgeTextChunker {

    String version();

    List<KnowledgeTextChunk> split(List<KnowledgeTextDocument> documents);
}
