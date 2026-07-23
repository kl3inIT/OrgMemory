-- Per-connection identity trust. An admin attests once, for a whole connection,
-- whether the source workspace is SSO/SCIM provisioned. That decision is what
-- unlocks the SSO_EMAIL_JOIN mapping tier; without it an observed email is an
-- unverified claim and only IDP_JOIN or ADMIN_CONFIRMED may resolve a principal.

CREATE TABLE source_connections (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    source_system varchar(64) NOT NULL,
    source_connection_key varchar(128) NOT NULL,
    identity_trust varchar(32) NOT NULL DEFAULT 'UNTRUSTED',
    trust_decided_by_user_id uuid,
    trust_decided_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_source_connection_identity UNIQUE (
        organization_id,
        source_system,
        source_connection_key
    ),
    CONSTRAINT fk_source_connection_decided_by
        FOREIGN KEY (trust_decided_by_user_id, organization_id)
        REFERENCES app_users(id, organization_id),
    CONSTRAINT chk_source_connection_identity_trust
        CHECK (identity_trust IN ('UNTRUSTED', 'SSO_VERIFIED')),
    CONSTRAINT chk_source_connection_nonblank CHECK (
        btrim(source_system) <> ''
        AND btrim(source_connection_key) <> ''
    ),
    -- Trust is an accountable decision: raising it records who made it and when, and
    -- lowering it back to the default clears that attribution rather than leaving
    -- stale evidence behind claiming somebody still vouches for the connection.
    CONSTRAINT chk_source_connection_trust_attribution CHECK (
        (identity_trust = 'UNTRUSTED'
            AND trust_decided_by_user_id IS NULL
            AND trust_decided_at IS NULL)
        OR (identity_trust <> 'UNTRUSTED'
            AND trust_decided_by_user_id IS NOT NULL
            AND trust_decided_at IS NOT NULL)
    )
);
