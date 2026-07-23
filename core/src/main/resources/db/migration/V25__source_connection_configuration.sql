-- Crawl configuration moves out of environment variables and onto the connection row.
-- source_connections is already keyed by the connection and already carries the
-- identity-trust decision, so the configuration belongs there rather than in a
-- parallel table that would leave two records of one thing.

ALTER TABLE source_connections
    ADD COLUMN crawl_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN knowledge_space_id uuid REFERENCES knowledge_spaces(id),
    ADD COLUMN actor_user_id uuid,
    ADD COLUMN channel_filter varchar(2048) NOT NULL DEFAULT '',
    ADD COLUMN content_crawl_interval_seconds integer NOT NULL DEFAULT 3600,
    ADD COLUMN max_threads_per_channel integer NOT NULL DEFAULT 500,
    ADD COLUMN crawl_configured_by_user_id uuid,
    ADD COLUMN crawl_configured_at timestamptz;

ALTER TABLE source_connections
    ADD CONSTRAINT fk_source_connection_actor
        FOREIGN KEY (actor_user_id, organization_id) REFERENCES app_users(id, organization_id),
    ADD CONSTRAINT fk_source_connection_configured_by
        FOREIGN KEY (crawl_configured_by_user_id, organization_id) REFERENCES app_users(id, organization_id),
    -- A crawl needs somewhere to publish and somebody to publish as. Enabling without
    -- either would fail per object at ingestion time, long after the mistake was made.
    ADD CONSTRAINT chk_source_connection_crawl_targets CHECK (
        crawl_enabled = false
        OR (knowledge_space_id IS NOT NULL AND actor_user_id IS NOT NULL)
    ),
    ADD CONSTRAINT chk_source_connection_crawl_bounds CHECK (
        content_crawl_interval_seconds > 0 AND max_threads_per_channel > 0
    );

COMMENT ON COLUMN source_connections.channel_filter IS
    'Comma-separated channel names to crawl. Empty means every channel the bot can see; '
    'a non-empty filter also stops the crawl claiming it enumerated the connection.';

-- The credential lives apart from the connection on purpose. The connection row is read
-- on every crawl and rendered in the admin list, so keeping the ciphertext in another
-- table means no query that builds that view can reach the secret by accident.
CREATE TABLE source_connection_credentials (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    source_connection_id uuid NOT NULL UNIQUE REFERENCES source_connections(id) ON DELETE CASCADE,
    cipher_text text NOT NULL,
    key_version integer NOT NULL,
    set_by_user_id uuid NOT NULL,
    set_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT fk_source_connection_credential_set_by
        FOREIGN KEY (set_by_user_id, organization_id) REFERENCES app_users(id, organization_id),
    CONSTRAINT chk_source_connection_credential_cipher CHECK (btrim(cipher_text) <> ''),
    CONSTRAINT chk_source_connection_credential_key_version CHECK (key_version >= 1)
);

COMMENT ON TABLE source_connection_credentials IS
    'Encrypted source credentials. cipher_text is AES-256-GCM ciphertext; key_version records '
    'which key produced it so a rotation can select what it has yet to re-encrypt rather than '
    'trial-decrypting every row.';
