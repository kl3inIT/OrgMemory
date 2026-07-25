CREATE TABLE public.assistant_asset_traces (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    action varchar(48) NOT NULL,
    release_refs jsonb NOT NULL,
    knowledge_refs jsonb NOT NULL,
    model_route jsonb NOT NULL,
    tool_call jsonb NOT NULL,
    authorization_context jsonb NOT NULL,
    sanitized_outcome jsonb NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT assistant_asset_traces_pkey PRIMARY KEY (id),
    CONSTRAINT assistant_asset_trace_release_refs_check
        CHECK (jsonb_typeof(release_refs) = 'array'),
    CONSTRAINT assistant_asset_trace_knowledge_refs_check
        CHECK (jsonb_typeof(knowledge_refs) = 'array'),
    CONSTRAINT assistant_asset_trace_model_route_check
        CHECK (jsonb_typeof(model_route) = 'object'),
    CONSTRAINT assistant_asset_trace_tool_call_check
        CHECK (jsonb_typeof(tool_call) = 'object'),
    CONSTRAINT assistant_asset_trace_authorization_check
        CHECK (jsonb_typeof(authorization_context) = 'object'),
    CONSTRAINT assistant_asset_trace_outcome_check
        CHECK (jsonb_typeof(sanitized_outcome) = 'object'),
    CONSTRAINT fk_assistant_asset_trace_actor
        FOREIGN KEY (actor_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE INDEX idx_assistant_asset_trace_actor
    ON public.assistant_asset_traces (
        organization_id, actor_user_id, occurred_at DESC
    );

CREATE TABLE public.assistant_asset_feedback (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    asset_id uuid NOT NULL,
    release_id uuid NOT NULL,
    release_digest varchar(64) NOT NULL,
    actor_user_id uuid NOT NULL,
    type varchar(24) NOT NULL,
    comment varchar(2000) NOT NULL,
    submitted_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT assistant_asset_feedback_pkey PRIMARY KEY (id),
    CONSTRAINT assistant_asset_feedback_type_check CHECK (
        type IN ('HELPFUL', 'OUTDATED', 'INCORRECT', 'OTHER')
    ),
    CONSTRAINT fk_assistant_asset_feedback_release FOREIGN KEY (
        release_id, asset_id, organization_id
    ) REFERENCES public.asset_releases(id, asset_id, organization_id),
    CONSTRAINT fk_assistant_asset_feedback_actor
        FOREIGN KEY (actor_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE INDEX idx_assistant_asset_feedback_release
    ON public.assistant_asset_feedback (
        organization_id, asset_id, release_id, submitted_at DESC
    );

CREATE TRIGGER reject_assistant_asset_trace_mutation
    BEFORE UPDATE OR DELETE ON public.assistant_asset_traces
    FOR EACH ROW EXECUTE FUNCTION public.reject_asset_immutable_mutation();

CREATE TRIGGER reject_assistant_asset_feedback_mutation
    BEFORE UPDATE OR DELETE ON public.assistant_asset_feedback
    FOR EACH ROW EXECUTE FUNCTION public.reject_asset_immutable_mutation();
