CREATE TABLE public.ai_gateway_profiles (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    gateway_key varchar(64) NOT NULL,
    display_name varchar(120) NOT NULL,
    preset varchar(32) NOT NULL,
    category varchar(32) NOT NULL,
    protocol varchar(32) NOT NULL,
    base_url varchar(500) NOT NULL,
    request_timeout_seconds integer NOT NULL,
    enabled boolean NOT NULL,
    runtime_revision bigint NOT NULL,
    created_by_user_id uuid NOT NULL,
    updated_by_user_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_ai_gateway_profile_id_org UNIQUE (id, organization_id),
    CONSTRAINT uq_ai_gateway_profile_org_key UNIQUE (organization_id, gateway_key),
    CONSTRAINT fk_ai_gateway_profile_org FOREIGN KEY (organization_id)
        REFERENCES public.organizations(id),
    CONSTRAINT fk_ai_gateway_profile_created_by FOREIGN KEY (
        created_by_user_id,
        organization_id
    ) REFERENCES public.app_users(id, organization_id),
    CONSTRAINT fk_ai_gateway_profile_updated_by FOREIGN KEY (
        updated_by_user_id,
        organization_id
    ) REFERENCES public.app_users(id, organization_id),
    CONSTRAINT chk_ai_gateway_profile_key
        CHECK (gateway_key ~ '^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$'),
    CONSTRAINT chk_ai_gateway_profile_preset CHECK (
        preset IN (
            'OPENAI',
            'ANTHROPIC',
            'NINE_ROUTER',
            'OPENROUTER',
            'LITELLM',
            'OLLAMA',
            'OPENAI_COMPATIBLE'
        )
    ),
    CONSTRAINT chk_ai_gateway_profile_category CHECK (
        category IN (
            'DIRECT_PROVIDER',
            'GATEWAY_ROUTER',
            'SELF_HOSTED_CUSTOM'
        )
    ),
    CONSTRAINT chk_ai_gateway_profile_protocol CHECK (
        protocol IN ('OPENAI_COMPATIBLE', 'ANTHROPIC_MESSAGES')
    ),
    CONSTRAINT chk_ai_gateway_profile_timeout CHECK (
        request_timeout_seconds BETWEEN 1 AND 300
    ),
    CONSTRAINT chk_ai_gateway_profile_revision CHECK (runtime_revision > 0),
    CONSTRAINT chk_ai_gateway_profile_text CHECK (
        btrim(display_name) <> ''
        AND btrim(base_url) <> ''
    )
);

CREATE TABLE public.ai_gateway_credentials (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    gateway_profile_id uuid NOT NULL,
    cipher_text text NOT NULL,
    key_version integer NOT NULL,
    set_by_user_id uuid NOT NULL,
    set_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_ai_gateway_credential_profile UNIQUE (
        organization_id,
        gateway_profile_id
    ),
    CONSTRAINT fk_ai_gateway_credential_profile FOREIGN KEY (
        gateway_profile_id,
        organization_id
    ) REFERENCES public.ai_gateway_profiles(id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_ai_gateway_credential_set_by FOREIGN KEY (
        set_by_user_id,
        organization_id
    ) REFERENCES public.app_users(id, organization_id),
    CONSTRAINT chk_ai_gateway_credential_ciphertext CHECK (
        btrim(cipher_text) <> ''
    )
);

CREATE TABLE public.ai_route_overrides (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    workload varchar(32) NOT NULL,
    gateway_profile_id uuid NOT NULL,
    model_id varchar(200) NOT NULL,
    set_by_user_id uuid NOT NULL,
    set_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uq_ai_route_override_org_workload UNIQUE (
        organization_id,
        workload
    ),
    CONSTRAINT fk_ai_route_override_profile FOREIGN KEY (
        gateway_profile_id,
        organization_id
    ) REFERENCES public.ai_gateway_profiles(id, organization_id),
    CONSTRAINT fk_ai_route_override_set_by FOREIGN KEY (
        set_by_user_id,
        organization_id
    ) REFERENCES public.app_users(id, organization_id),
    CONSTRAINT chk_ai_route_override_workload CHECK (
        workload IN ('ASSISTANT_CHAT', 'PROMPT_EXECUTION')
    ),
    CONSTRAINT chk_ai_route_override_model CHECK (
        btrim(model_id) <> ''
    )
);

COMMENT ON TABLE public.ai_gateway_profiles IS
    'Organization-scoped runtime AI connections. base_url is public configuration; credentials are held only as ciphertext in ai_gateway_credentials.';

COMMENT ON TABLE public.ai_gateway_credentials IS
    'Write-only encrypted AI provider credentials. No API response or audit event may expose cipher_text or derived secret material.';

COMMENT ON TABLE public.ai_route_overrides IS
    'Explicit organization workload overrides. Absence means deployment default; a selected provider failure never falls through to that default.';
