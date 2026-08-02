ALTER TABLE public.ai_gateway_profiles
    ADD COLUMN supports_openai_reasoning_effort boolean NOT NULL DEFAULT false;

ALTER TABLE public.ai_gateway_profiles
    ALTER COLUMN supports_openai_reasoning_effort DROP DEFAULT;

ALTER TABLE public.ai_route_overrides
    ADD COLUMN openai_reasoning_effort varchar(16);

ALTER TABLE public.ai_route_overrides
    DROP CONSTRAINT chk_ai_route_override_workload;

ALTER TABLE public.ai_route_overrides
    ADD CONSTRAINT chk_ai_route_override_workload CHECK (
        workload IN (
            'ASSISTANT_CHAT',
            'PROMPT_EXECUTION',
            'KEYWORD_PLANNING'
        )
    );

ALTER TABLE public.ai_route_overrides
    ADD CONSTRAINT chk_ai_route_override_openai_reasoning CHECK (
        openai_reasoning_effort IS NULL
        OR openai_reasoning_effort IN (
            'NONE',
            'LOW',
            'MEDIUM',
            'HIGH',
            'XHIGH',
            'MAX'
        )
    );

COMMENT ON COLUMN public.ai_gateway_profiles.supports_openai_reasoning_effort IS
    'Operator declaration that this OpenAI-compatible endpoint accepts reasoning_effort. False fails closed for configured route options.';

COMMENT ON COLUMN public.ai_route_overrides.openai_reasoning_effort IS
    'Optional OpenAI-specific route option. NULL means omit the field and use the provider default.';
