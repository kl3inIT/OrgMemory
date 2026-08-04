CREATE TABLE public.ai_assistant_model_activations (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    gateway_profile_id uuid NOT NULL,
    model_id varchar(200) NOT NULL,
    display_name varchar(200) NOT NULL,
    enabled boolean NOT NULL,
    enabled_by_user_id uuid NOT NULL,
    disabled_by_user_id uuid,
    disabled_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT ai_assistant_model_activations_pkey PRIMARY KEY (id),
    CONSTRAINT uq_ai_assistant_model_activation_org UNIQUE (id, organization_id),
    CONSTRAINT fk_ai_assistant_model_activation_profile FOREIGN KEY (
        gateway_profile_id,
        organization_id
    ) REFERENCES public.ai_gateway_profiles(id, organization_id),
    CONSTRAINT fk_ai_assistant_model_activation_enabled_by FOREIGN KEY (
        enabled_by_user_id,
        organization_id
    ) REFERENCES public.app_users(id, organization_id),
    CONSTRAINT fk_ai_assistant_model_activation_disabled_by FOREIGN KEY (
        disabled_by_user_id,
        organization_id
    ) REFERENCES public.app_users(id, organization_id),
    CONSTRAINT chk_ai_assistant_model_activation_text CHECK (
        length(btrim(model_id)) BETWEEN 1 AND 200
        AND length(btrim(display_name)) BETWEEN 1 AND 200
    ),
    CONSTRAINT chk_ai_assistant_model_activation_state CHECK (
        (enabled AND disabled_by_user_id IS NULL AND disabled_at IS NULL)
        OR
        (NOT enabled AND disabled_by_user_id IS NOT NULL AND disabled_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_ai_assistant_model_activation_active
    ON public.ai_assistant_model_activations (
        organization_id,
        gateway_profile_id,
        model_id
    )
    WHERE enabled;

CREATE INDEX idx_ai_assistant_model_activation_catalog
    ON public.ai_assistant_model_activations (
        organization_id,
        gateway_profile_id,
        enabled,
        display_name,
        model_id
    );

ALTER TABLE public.assistant_conversations
    ADD COLUMN selected_model_activation_id uuid,
    ADD COLUMN selected_route_override_id uuid,
    ADD COLUMN selected_route_override_version bigint;

ALTER TABLE public.assistant_conversations
    ADD CONSTRAINT fk_assistant_conversation_model_activation FOREIGN KEY (
        selected_model_activation_id,
        organization_id
    ) REFERENCES public.ai_assistant_model_activations(id, organization_id),
    ADD CONSTRAINT chk_assistant_conversation_model_selection CHECK (
        (
            selected_model_activation_id IS NULL
            AND selected_route_override_id IS NULL
            AND selected_route_override_version IS NULL
        )
        OR
        (
            selected_model_activation_id IS NOT NULL
            AND selected_route_override_id IS NOT NULL
            AND selected_route_override_version >= 0
        )
    );

CREATE INDEX idx_assistant_conversation_model_activation
    ON public.assistant_conversations (
        organization_id,
        selected_model_activation_id
    )
    WHERE selected_model_activation_id IS NOT NULL;

COMMENT ON TABLE public.ai_assistant_model_activations IS
    'Immutable-identity administrator activations for additional chat models on the current organization Assistant gateway. Disabled rows remain to prevent stale selection revival.';

COMMENT ON COLUMN public.assistant_conversations.selected_route_override_version IS
    'Route generation observed when the actor selected the catalog activation; exact mismatch invalidates the selection, including A-to-B-to-A changes.';
