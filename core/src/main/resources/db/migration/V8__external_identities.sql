CREATE TABLE external_identities (
    id uuid PRIMARY KEY,
    app_user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    issuer varchar(512) NOT NULL,
    subject varchar(255) NOT NULL,
    linked_email varchar(255) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_external_identity_issuer_subject UNIQUE (issuer, subject),
    CONSTRAINT uq_external_identity_user_issuer UNIQUE (app_user_id, issuer)
);

CREATE UNIQUE INDEX uq_app_users_email_lower ON app_users (lower(email));
CREATE INDEX idx_external_identities_app_user_id ON external_identities(app_user_id);

INSERT INTO app_users (id, organization_id, department_id, name, email, role, created_at, updated_at, version)
VALUES (
    '13000000-0000-4000-8000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    '99999999-9999-9999-9999-999999999999',
    'Org Admin',
    'orgadmin@example.com',
    'ADMIN',
    now(),
    now(),
    0
);
