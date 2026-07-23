ALTER TABLE source_revisions
    VALIDATE CONSTRAINT chk_source_revision_processing_profile;

ALTER TABLE knowledge_chunks
    VALIDATE CONSTRAINT chk_knowledge_chunk_source_span;

ALTER TABLE knowledge_chunks
    VALIDATE CONSTRAINT chk_knowledge_chunk_block_indexes;
