-- Every crawl outcome was a log line. That answers "why is nothing appearing from
-- Slack?" only for whoever can read the worker's stdout, which is not the administrator
-- who configured the connection and is looking at a screen that says it is enabled.
--
-- Onyx keeps the same fact as a row per attempt and builds its whole connector detail
-- page on it. This is that table, without the fields that exist because their indexing
-- is a distributed long-running job: no heartbeat, no cancellation flag, no batch
-- counters. A crawl here is one synchronous pass in one worker, so an attempt has an
-- outcome and counts, and that is all there is to record.
--
-- The checkpoint table is not this. It says how far a connection got, one row, always
-- overwritten. This says what happened each time, kept, including the times that ended
-- in nothing.

CREATE TABLE connector_crawl_attempts (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    source_system varchar(64) NOT NULL,
    source_connection_key varchar(128) NOT NULL,
    -- Null when the source could not produce a batch for this connection at all. There is no
    -- cursor for work that was never offered, and inventing one would make a crawl that never
    -- happened indistinguishable from one that did.
    crawl_cursor varchar(512),
    outcome varchar(32) NOT NULL,
    objects_materialized integer NOT NULL,
    objects_rotated integer NOT NULL,
    objects_rematerialized integer NOT NULL,
    objects_retired integer NOT NULL,
    objects_failed integer NOT NULL,
    error_code varchar(64),
    error_message varchar(512),
    attempted_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_connector_crawl_attempt_nonblank CHECK (
        btrim(source_system) <> ''
        AND btrim(source_connection_key) <> ''
        AND (crawl_cursor IS NULL OR btrim(crawl_cursor) <> '')
    ),
    -- SUCCEEDED is the whole batch reconciled; REJECTED is a batch no retry can fix and
    -- which was checkpointed past; FAILED is a batch left for the next poll; UNAVAILABLE is
    -- the connection producing no batch at all, which is what a revoked token looks like.
    CONSTRAINT chk_connector_crawl_attempt_outcome
        CHECK (outcome IN ('SUCCEEDED', 'REJECTED', 'FAILED', 'UNAVAILABLE')),
    -- Every outcome except UNAVAILABLE describes something that happened to a batch, and a
    -- batch is its cursor.
    CONSTRAINT chk_connector_crawl_attempt_cursor CHECK (
        (outcome = 'UNAVAILABLE' AND crawl_cursor IS NULL)
        OR (outcome <> 'UNAVAILABLE' AND crawl_cursor IS NOT NULL)
    ),
    CONSTRAINT chk_connector_crawl_attempt_counts CHECK (
        objects_materialized >= 0
        AND objects_rotated >= 0
        AND objects_rematerialized >= 0
        AND objects_retired >= 0
        AND objects_failed >= 0
    ),
    -- An attempt that did not succeed and does not say why is a row nobody can act on,
    -- and a success carrying an error message is a contradiction. Both are refused here
    -- rather than left to whoever writes the next caller.
    CONSTRAINT chk_connector_crawl_attempt_reason CHECK (
        (outcome = 'SUCCEEDED' AND error_code IS NULL AND error_message IS NULL)
        OR (outcome <> 'SUCCEEDED' AND btrim(error_message) <> '')
    )
);

-- The only question asked of this table is "what happened lately on this connection",
-- so the index carries the ordering as well as the filter.
CREATE INDEX idx_connector_crawl_attempt_recent ON connector_crawl_attempts (
    organization_id,
    source_system,
    source_connection_key,
    attempted_at DESC
);

COMMENT ON TABLE connector_crawl_attempts IS
    'One row per crawl batch a driver acted on, kept so an administrator can see why a '
    'connection is producing nothing without reading worker logs. error_message carries a '
    'diagnostic only: the credential travels in an Authorization header and never appears '
    'in an adapter exception message, and nothing here may be allowed to change that.';
