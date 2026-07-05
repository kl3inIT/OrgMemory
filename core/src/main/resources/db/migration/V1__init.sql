CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE organizations (
    id uuid PRIMARY KEY,
    name varchar(255) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL
);

CREATE TABLE departments (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    name varchar(255) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL
);

CREATE TABLE app_users (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    department_id uuid REFERENCES departments(id),
    name varchar(255) NOT NULL,
    email varchar(255) NOT NULL,
    role varchar(255) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL
);

CREATE TABLE capability_assets (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    department_id uuid REFERENCES departments(id),
    title varchar(255) NOT NULL,
    summary text NOT NULL,
    use_case varchar(255),
    business_process varchar(255),
    owner_user_id uuid REFERENCES app_users(id),
    backup_owner_user_id uuid REFERENCES app_users(id),
    status varchar(255) NOT NULL,
    visibility varchar(255) NOT NULL,
    risk_level varchar(255),
    current_version_id uuid,
    created_by_user_id uuid REFERENCES app_users(id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL
);

CREATE TABLE asset_versions (
    id uuid PRIMARY KEY,
    asset_id uuid NOT NULL REFERENCES capability_assets(id) ON DELETE CASCADE,
    version_number integer NOT NULL,
    prompt_template text,
    workflow_steps_json text,
    input_schema_json text,
    output_schema_json text,
    example_input text,
    example_output text,
    change_note varchar(255),
    created_by_user_id uuid REFERENCES app_users(id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL
);

CREATE TABLE asset_usage_events (
    id uuid PRIMARY KEY,
    asset_id uuid NOT NULL REFERENCES capability_assets(id) ON DELETE CASCADE,
    user_id uuid REFERENCES app_users(id),
    event_type varchar(255) NOT NULL,
    metadata_json text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL
);

CREATE TABLE asset_approval_events (
    id uuid PRIMARY KEY,
    asset_id uuid NOT NULL REFERENCES capability_assets(id) ON DELETE CASCADE,
    reviewer_user_id uuid REFERENCES app_users(id),
    action varchar(255) NOT NULL,
    comment text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL
);

CREATE TABLE tags (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    name varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE asset_tags (
    asset_id uuid NOT NULL REFERENCES capability_assets(id) ON DELETE CASCADE,
    tag_id uuid NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (asset_id, tag_id)
);

CREATE TABLE asset_embeddings (
    id uuid PRIMARY KEY,
    asset_id uuid NOT NULL REFERENCES capability_assets(id) ON DELETE CASCADE,
    version_id uuid REFERENCES asset_versions(id) ON DELETE CASCADE,
    content text NOT NULL,
    embedding vector(1536),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_capability_assets_status ON capability_assets(status);
CREATE INDEX idx_capability_assets_department ON capability_assets(department_id);
CREATE INDEX idx_asset_versions_asset_id ON asset_versions(asset_id);
CREATE INDEX idx_asset_usage_events_asset_id ON asset_usage_events(asset_id);

INSERT INTO organizations (id, name, created_at, updated_at, version)
VALUES ('11111111-1111-1111-1111-111111111111', 'Demo Organization', now(), now(), 0);

INSERT INTO departments (id, organization_id, name, created_at, updated_at, version)
VALUES
    ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Sales', now(), now(), 0),
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Customer Success', now(), now(), 0);

INSERT INTO app_users (id, organization_id, department_id, name, email, role, created_at, updated_at, version)
VALUES
    ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Linh Nguyen', 'linh@example.com', 'EMPLOYEE', now(), now(), 0),
    ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Minh Tran', 'minh@example.com', 'TEAM_LEAD', now(), now(), 0);

INSERT INTO capability_assets (
    id, organization_id, department_id, title, summary, use_case, business_process,
    owner_user_id, backup_owner_user_id, status, visibility, risk_level, current_version_id,
    created_by_user_id, created_at, updated_at, version
) VALUES (
    '66666666-6666-6666-6666-666666666666',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'Post-demo follow-up email',
    'Generates a concise follow-up email after a B2B product demo.',
    'Sales follow-up',
    'Opportunity management',
    '44444444-4444-4444-4444-444444444444',
    '55555555-5555-5555-5555-555555555555',
    'APPROVED',
    'TEAM',
    'LOW',
    '77777777-7777-7777-7777-777777777777',
    '44444444-4444-4444-4444-444444444444',
    now(),
    now(),
    0
);

INSERT INTO asset_versions (
    id, asset_id, version_number, prompt_template, workflow_steps_json, input_schema_json,
    output_schema_json, example_input, example_output, change_note, created_by_user_id,
    created_at, updated_at, version
) VALUES (
    '77777777-7777-7777-7777-777777777777',
    '66666666-6666-6666-6666-666666666666',
    1,
    'Write a follow-up email to {{customer_name}} after a demo. Mention {{pain_points}}, {{interested_features}}, and propose {{next_step}}.',
    '[{"name":"Collect demo notes"},{"name":"Generate email"},{"name":"Review before sending"}]',
    '{"customer_name":"string","pain_points":"string","interested_features":"string","next_step":"string"}',
    '{"subject":"string","body":"string"}',
    'Customer Acme needs faster onboarding and asked about analytics.',
    'Subject: Next steps after our demo\n\nHi Acme team, thank you for joining the demo...',
    'Initial seed version',
    '44444444-4444-4444-4444-444444444444',
    now(),
    now(),
    0
);
