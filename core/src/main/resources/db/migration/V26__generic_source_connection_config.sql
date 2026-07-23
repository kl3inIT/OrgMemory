-- A connection's settings split into two kinds, and only one of them can be typed.
--
-- Every source needs somewhere to publish, somebody to publish as, a switch, and a cadence.
-- Those keep real columns and keep their check constraints, because violating one is a
-- governance failure rather than a bad crawl: a crawl enabled with no Knowledge Space would
-- fail once per object, long after the mistake was made.
--
-- Channels and a per-channel thread bound are Slack's vocabulary. Google Drive has folders,
-- Confluence has spaces, and there is no honest column shape covering all of them, so they
-- move into a document this table does not interpret. Nothing here reads inside it; the
-- adapter that wrote the shape is the only thing that understands it.

ALTER TABLE source_connections ADD COLUMN source_config jsonb NOT NULL DEFAULT '{}'::jsonb;

UPDATE source_connections
SET source_config = jsonb_build_object(
    'channels',
    COALESCE(
        (SELECT jsonb_agg(btrim(channel))
         FROM unnest(string_to_array(channel_filter, ',')) AS channel
         WHERE btrim(channel) <> ''),
        '[]'::jsonb),
    'maxThreadsPerChannel', to_jsonb(max_threads_per_channel));

-- Dropping either column would take this constraint with it, and it also guards the interval,
-- so it is replaced rather than left to be collateral damage.
ALTER TABLE source_connections DROP CONSTRAINT chk_source_connection_crawl_bounds;

ALTER TABLE source_connections
    DROP COLUMN channel_filter,
    DROP COLUMN max_threads_per_channel;

ALTER TABLE source_connections
    ADD CONSTRAINT chk_source_connection_crawl_interval
        CHECK (content_crawl_interval_seconds > 0),
    -- An array or a bare string here would still be valid JSON and would break every reader.
    ADD CONSTRAINT chk_source_connection_config_object
        CHECK (jsonb_typeof(source_config) = 'object');

COMMENT ON COLUMN source_connections.source_config IS
    'Settings only this source system understands, as a JSON object. The ledger stores and '
    'returns it without reading inside: a column shape covering every source does not exist, '
    'and pretending otherwise is what made adding a source a migration.';
