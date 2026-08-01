package com.orgmemory.core.knowledge.sourceledger;

/** Source Ledger-owned command for atomically publishing a completed source revision. */
public record CompleteSourceRevisionCommand(
        SourceRevisionDraftRef draft,
        String pipelineVersion,
        String parserVersion,
        String chunkerVersion,
        DocumentProcessingProfileSnapshot processingProfile,
        SourceEmbeddingProfileRef embeddingProfile,
        RawSourceRef rawSource,
        NormalizedRecordRef normalizedRecord,
        SourceKnowledgeAssetRef knowledgeAsset) {}
