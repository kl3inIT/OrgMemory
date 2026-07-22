-- External principal mapping: observed source principals, verified mappings to
-- internal users, and sealed per-generation group membership. Observation
-- grants nothing; only verified mappings resolve external principals.

CREATE TABLE source_principals (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    source_system varchar(64) NOT NULL,
    source_connection_key varchar(128) NOT NULL,
    external_key varchar(512) NOT NULL,
    kind varchar(16) NOT NULL,
    observed_email varchar(320),
    observed_display_name varchar(256),
    sso_verified boolean NOT NULL DEFAULT false,
    last_seen_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_source_principal_identity UNIQUE (
        organization_id,
        source_system,
        source_connection_key,
        external_key
    ),
    CONSTRAINT uq_source_principal_id_organization UNIQUE (id, organization_id),
    CONSTRAINT uq_source_principal_id_organization_kind
        UNIQUE (id, organization_id, kind),
    CONSTRAINT chk_source_principal_kind
        CHECK (kind IN ('SOURCE_USER', 'SOURCE_GROUP')),
    CONSTRAINT chk_source_principal_nonblank CHECK (
        btrim(source_system) <> ''
        AND btrim(source_connection_key) <> ''
        AND btrim(external_key) <> ''
    ),
    CONSTRAINT chk_source_principal_email
        CHECK (observed_email IS NULL OR btrim(observed_email) <> '')
);

CREATE TABLE source_principal_mappings (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    source_principal_id uuid NOT NULL,
    source_principal_kind varchar(16) NOT NULL DEFAULT 'SOURCE_USER',
    app_user_id uuid NOT NULL,
    method varchar(32) NOT NULL,
    evidence varchar(512) NOT NULL,
    status varchar(16) NOT NULL,
    verified_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT fk_source_principal_mapping_principal_kind
        FOREIGN KEY (source_principal_id, organization_id, source_principal_kind)
        REFERENCES source_principals(id, organization_id, kind),
    CONSTRAINT fk_source_principal_mapping_user_organization
        FOREIGN KEY (app_user_id, organization_id)
        REFERENCES app_users(id, organization_id),
    CONSTRAINT chk_source_principal_mapping_kind
        CHECK (source_principal_kind = 'SOURCE_USER'),
    CONSTRAINT chk_source_principal_mapping_method CHECK (method IN (
        'IDP_JOIN',
        'SSO_EMAIL_JOIN',
        'SELF_CLAIM',
        'ADMIN_CONFIRMED'
    )),
    CONSTRAINT chk_source_principal_mapping_status CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
    ),
    CONSTRAINT chk_source_principal_mapping_evidence CHECK (btrim(evidence) <> '')
);

CREATE UNIQUE INDEX uq_source_principal_mapping_active
    ON source_principal_mappings (source_principal_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_source_principal_mapping_user
    ON source_principal_mappings (organization_id, app_user_id)
    WHERE status = 'ACTIVE';

CREATE TABLE source_acl_group_members (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    source_acl_snapshot_id uuid NOT NULL,
    group_principal_id uuid NOT NULL,
    group_principal_kind varchar(16) NOT NULL DEFAULT 'SOURCE_GROUP',
    member_principal_id uuid NOT NULL,
    member_principal_kind varchar(16) NOT NULL DEFAULT 'SOURCE_USER',
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_source_acl_group_member UNIQUE (
        source_acl_snapshot_id,
        group_principal_id,
        member_principal_id
    ),
    CONSTRAINT fk_source_acl_group_member_snapshot
        FOREIGN KEY (source_acl_snapshot_id, organization_id)
        REFERENCES source_acl_snapshots(id, organization_id),
    CONSTRAINT fk_source_acl_group_member_group
        FOREIGN KEY (group_principal_id, organization_id, group_principal_kind)
        REFERENCES source_principals(id, organization_id, kind),
    CONSTRAINT fk_source_acl_group_member_member
        FOREIGN KEY (member_principal_id, organization_id, member_principal_kind)
        REFERENCES source_principals(id, organization_id, kind),
    CONSTRAINT chk_source_acl_group_member_group_kind
        CHECK (group_principal_kind = 'SOURCE_GROUP'),
    CONSTRAINT chk_source_acl_group_member_member_kind
        CHECK (member_principal_kind = 'SOURCE_USER')
);

CREATE INDEX idx_source_acl_group_member_member
    ON source_acl_group_members (organization_id, member_principal_id);

-- Membership rows are sealed evidence: no inserts after the snapshot seal and
-- no mutation ever, reusing the existing sealed-ACL trigger functions.

CREATE TRIGGER source_acl_group_members_reject_after_seal
BEFORE INSERT
ON source_acl_group_members
FOR EACH ROW
EXECUTE FUNCTION reject_entry_insert_into_sealed_acl();

CREATE TRIGGER source_acl_group_members_append_only
BEFORE UPDATE OR DELETE OR TRUNCATE
ON source_acl_group_members
FOR EACH STATEMENT
EXECUTE FUNCTION reject_source_acl_evidence_mutation();

-- ACL entries may now reference external principals; retrieval resolves them
-- only through verified mappings and stays fail-closed until it does.

ALTER TABLE source_acl_entries
    DROP CONSTRAINT chk_source_acl_principal_type;

ALTER TABLE source_acl_entries
    ADD CONSTRAINT chk_source_acl_principal_type
    CHECK (principal_type IN (
        'ORGMEMORY_USER',
        'ORGMEMORY_DEPARTMENT',
        'ORGMEMORY_ORGANIZATION',
        'SOURCE_USER',
        'SOURCE_GROUP'
    ));
