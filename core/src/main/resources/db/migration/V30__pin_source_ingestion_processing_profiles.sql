-- The old worker cannot reconstruct a complete requested snapshot for work it
-- already started. Serialize this check against enqueue/claim and require the
-- operator to stop and drain the old worker before the schema cutover.
LOCK TABLE source_ingestion_jobs IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM source_ingestion_jobs
        WHERE status IN ('PENDING', 'PROCESSING')
    ) THEN
        RAISE EXCEPTION
            'V30 requires legacy source ingestion jobs to be drained before migration';
    END IF;
END
$$;

ALTER TABLE source_ingestion_jobs
    ADD COLUMN requested_processing_profile text,
    ADD COLUMN requested_processing_profile_sha256 varchar(64),
    ADD COLUMN resolved_processing_profile text,
    ADD COLUMN resolved_processing_profile_sha256 varchar(64);

ALTER TABLE source_ingestion_jobs
    ADD CONSTRAINT chk_source_ingestion_requested_profile
        CHECK (
            (requested_processing_profile IS NULL
                AND requested_processing_profile_sha256 IS NULL)
            OR
            (requested_processing_profile IS NOT NULL
                AND requested_processing_profile_sha256 IS NOT NULL
                AND btrim(requested_processing_profile) <> ''
                AND requested_processing_profile_sha256 ~ '^[0-9a-f]{64}$')
        ),
    ADD CONSTRAINT chk_source_ingestion_resolved_profile
        CHECK (
            (resolved_processing_profile IS NULL
                AND resolved_processing_profile_sha256 IS NULL)
            OR
            (resolved_processing_profile IS NOT NULL
                AND resolved_processing_profile_sha256 IS NOT NULL
                AND btrim(resolved_processing_profile) <> ''
                AND resolved_processing_profile_sha256 ~ '^[0-9a-f]{64}$')
        );

COMMENT ON COLUMN source_ingestion_jobs.requested_processing_profile IS
    'Canonical requested parser/policy/options snapshot pinned before the first processing side effect.';
COMMENT ON COLUMN source_ingestion_jobs.resolved_processing_profile IS
    'Canonical resolved parser/chunker/profile snapshot pinned before downstream publication.';

ALTER TABLE source_revisions DROP CONSTRAINT chk_source_revision_ready;
ALTER TABLE source_revisions
    ADD CONSTRAINT chk_source_revision_ready CHECK (
        status <> 'READY'
        OR (
            pipeline_version IS NOT NULL
            AND parser_version IS NOT NULL
            AND chunker_version IS NOT NULL
            AND embedding_profile_id IS NOT NULL
            AND embedding_dimensions IS NOT NULL
            AND knowledge_asset_id IS NOT NULL
            AND processed_at IS NOT NULL
            AND failure_code IS NULL
            AND processing_profile IS NOT NULL
            AND btrim(processing_profile) <> ''
            AND processing_profile_sha256 IS NOT NULL
            AND processing_profile_sha256 ~ '^[0-9a-f]{64}$'
        )
    ) NOT VALID;
