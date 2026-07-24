-- Signing in to the identity provider has never been enough to reach OrgMemory: the OIDC
-- subject must already be linked to an app user, and nothing creates that link. Users were
-- therefore inserted by hand, and an unlinked person was indistinguishable from a person the
-- identity provider has never heard of.
--
-- An invitation is the missing record. It names an email an administrator expects, and the
-- first sign-in matching it creates the user and the identity link. Access is still refused
-- when no invitation matches, so this widens nothing on its own — it moves the decision from
-- a manual INSERT to an auditable row.
CREATE TABLE user_invitations (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    email varchar(255) NOT NULL,
    department_id uuid REFERENCES departments(id),
    role varchar(32) NOT NULL,
    invited_by_user_id uuid NOT NULL REFERENCES app_users(id),
    revoked_at timestamptz,
    accepted_at timestamptz,
    accepted_app_user_id uuid REFERENCES app_users(id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_user_invitation_email CHECK (btrim(email) <> ''),
    CONSTRAINT chk_user_invitation_role CHECK (
        role IN ('EMPLOYEE', 'TEAM_LEAD', 'MANAGER', 'DIRECTOR', 'EXECUTIVE', 'ADMIN')
    ),
    -- An accepted invitation must say which user it produced, and an open one must not.
    CONSTRAINT chk_user_invitation_acceptance CHECK (
        (accepted_at IS NULL AND accepted_app_user_id IS NULL)
        OR (accepted_at IS NOT NULL AND accepted_app_user_id IS NOT NULL)
    ),
    CONSTRAINT chk_user_invitation_terminal CHECK (revoked_at IS NULL OR accepted_at IS NULL)
);

-- One open invitation per address. Accepted and revoked rows stay for the audit trail, so the
-- uniqueness is partial rather than over the whole table.
CREATE UNIQUE INDEX uq_user_invitation_open
    ON user_invitations (organization_id, lower(email))
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

CREATE INDEX idx_user_invitation_lookup
    ON user_invitations (lower(email))
    WHERE accepted_at IS NULL AND revoked_at IS NULL;
