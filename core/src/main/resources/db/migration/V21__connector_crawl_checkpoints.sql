-- Durable crawl checkpoints. The staging driver tracked processed cursors in an
-- in-process set, so a restart re-offered every batch the producer still had.
-- The cursor is opaque to OrgMemory: only the producer knows what it means, and
-- this table just remembers the last one a connection got through.
--
-- Correcting V20's header, which described the identity-trust decision as the
-- only thing that can unlock SSO_EMAIL_JOIN. It is not a gate over the source's
-- own vouching but a second, independent way to earn the same tier, for sources
-- that cannot vouch for the addresses they report. That migration is already
-- applied and its checksum must not move, so the record is corrected here.

CREATE TABLE connector_crawl_checkpoints (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    source_system varchar(64) NOT NULL,
    source_connection_key varchar(128) NOT NULL,
    crawl_cursor varchar(512) NOT NULL,
    checkpointed_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_connector_checkpoint_connection UNIQUE (
        organization_id,
        source_system,
        source_connection_key
    ),
    CONSTRAINT chk_connector_checkpoint_nonblank CHECK (
        btrim(source_system) <> ''
        AND btrim(source_connection_key) <> ''
        AND btrim(crawl_cursor) <> ''
    )
);

COMMENT ON TABLE connector_crawl_checkpoints IS
    'Last crawl cursor a connection completed, so an interrupted or restarted driver resumes '
    'instead of replaying. One row per connection; the cursor is opaque to OrgMemory.';
