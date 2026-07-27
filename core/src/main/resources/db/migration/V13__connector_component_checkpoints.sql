ALTER TABLE connector_crawl_checkpoints
    RENAME COLUMN crawl_cursor TO observed_cursor;

ALTER TABLE connector_crawl_checkpoints
    RENAME COLUMN checkpointed_at TO observed_at;

ALTER TABLE connector_crawl_checkpoints
    ADD COLUMN component_type varchar(16) NOT NULL DEFAULT 'CONTENT',
    ADD COLUMN capture_status varchar(16) NOT NULL DEFAULT 'COMPLETE',
    ADD COLUMN incomplete_reason varchar(500),
    ADD COLUMN last_successful_cursor varchar(512),
    ADD COLUMN last_successful_at timestamptz;

UPDATE connector_crawl_checkpoints
SET last_successful_cursor = observed_cursor,
    last_successful_at = observed_at;

ALTER TABLE connector_crawl_checkpoints
    ALTER COLUMN component_type DROP DEFAULT,
    ALTER COLUMN capture_status DROP DEFAULT,
    DROP CONSTRAINT uq_connector_checkpoint_connection,
    DROP CONSTRAINT chk_connector_checkpoint_nonblank;

ALTER TABLE connector_crawl_checkpoints
    ADD CONSTRAINT uq_connector_checkpoint_connection_component
        UNIQUE (
            organization_id,
            source_system,
            source_connection_key,
            component_type
        ),
    ADD CONSTRAINT chk_connector_checkpoint_component
        CHECK (component_type IN ('CONTENT', 'PERMISSION', 'MEMBERSHIP')),
    ADD CONSTRAINT chk_connector_checkpoint_capture_status
        CHECK (capture_status IN ('COMPLETE', 'INCOMPLETE')),
    ADD CONSTRAINT chk_connector_checkpoint_completeness
        CHECK (
            (capture_status = 'COMPLETE' AND incomplete_reason IS NULL)
            OR
            (
                capture_status = 'INCOMPLETE'
                AND incomplete_reason IS NOT NULL
                AND btrim(incomplete_reason) <> ''
            )
        ),
    ADD CONSTRAINT chk_connector_checkpoint_nonblank CHECK (
        btrim(source_system) <> ''
        AND btrim(source_connection_key) <> ''
        AND btrim(observed_cursor) <> ''
        AND (
            last_successful_cursor IS NULL
            OR btrim(last_successful_cursor) <> ''
        )
    );

COMMENT ON TABLE connector_crawl_checkpoints IS
    'Last observed and last successfully reconciled state for each connection component. Incomplete source evidence advances observation only.';

ALTER TABLE connector_crawl_attempts
    DROP CONSTRAINT chk_connector_crawl_attempt_outcome,
    DROP CONSTRAINT chk_connector_crawl_attempt_reason,
    ADD CONSTRAINT chk_connector_crawl_attempt_outcome
        CHECK (outcome IN ('SUCCEEDED', 'PARTIAL', 'REJECTED', 'FAILED', 'UNAVAILABLE')),
    ADD CONSTRAINT chk_connector_crawl_attempt_reason CHECK (
        (
            outcome = 'SUCCEEDED'
            AND error_code IS NULL
            AND error_message IS NULL
        )
        OR
        (
            outcome <> 'SUCCEEDED'
            AND error_message IS NOT NULL
            AND btrim(error_message) <> ''
        )
    );
