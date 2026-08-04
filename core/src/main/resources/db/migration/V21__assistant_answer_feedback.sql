ALTER TABLE public.assistant_conversation_messages
    ADD CONSTRAINT uq_assistant_message_tenant_actor
        UNIQUE (id, organization_id, actor_user_id);

CREATE TABLE public.assistant_answer_feedback (
    message_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    sentiment varchar(16) NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT assistant_answer_feedback_pkey PRIMARY KEY (message_id),
    CONSTRAINT assistant_answer_feedback_sentiment_check
        CHECK (sentiment IN ('HELPFUL', 'NOT_HELPFUL')),
    CONSTRAINT fk_assistant_answer_feedback_message
        FOREIGN KEY (message_id, organization_id, actor_user_id)
        REFERENCES public.assistant_conversation_messages(
            id, organization_id, actor_user_id
        )
        ON DELETE CASCADE
);

CREATE INDEX idx_assistant_answer_feedback_actor
    ON public.assistant_answer_feedback (
        organization_id, actor_user_id, updated_at DESC
    );
