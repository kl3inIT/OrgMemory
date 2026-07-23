ALTER TABLE source_revisions
    ADD COLUMN processing_profile text,
    ADD COLUMN processing_profile_sha256 varchar(64),
    ADD CONSTRAINT chk_source_revision_processing_profile CHECK (
        (
            processing_profile IS NULL
            AND processing_profile_sha256 IS NULL
        )
        OR (
            btrim(processing_profile) <> ''
            AND processing_profile_sha256 ~ '^[0-9a-f]{64}$'
        )
    ) NOT VALID;

ALTER TABLE knowledge_chunks
    ADD COLUMN source_start_char integer,
    ADD COLUMN source_end_char integer,
    ADD COLUMN source_block_indexes integer[] NOT NULL DEFAULT '{}',
    ADD COLUMN canonical_text_sha256 varchar(64),
    ADD CONSTRAINT chk_knowledge_chunk_source_span CHECK (
        (
            source_start_char IS NULL
            AND source_end_char IS NULL
            AND canonical_text_sha256 IS NULL
        )
        OR (
            source_start_char >= 0
            AND source_end_char > source_start_char
            AND canonical_text_sha256 ~ '^[0-9a-f]{64}$'
        )
    ) NOT VALID,
    ADD CONSTRAINT chk_knowledge_chunk_block_indexes CHECK (
        array_position(source_block_indexes, NULL) IS NULL
    ) NOT VALID;

COMMENT ON COLUMN source_revisions.processing_profile IS
    'Canonical resolved parser, chunker, tokenizer, model, and option snapshot.';
COMMENT ON COLUMN knowledge_chunks.source_start_char IS
    'Inclusive UTF-16 character offset into the canonical normalized document.';
COMMENT ON COLUMN knowledge_chunks.source_end_char IS
    'Exclusive UTF-16 character offset into the canonical normalized document.';
