-- Spring AI ChatMemory is the bounded context sent back to the model.
-- The complete product transcript is stored separately below.
CREATE TABLE public.spring_ai_chat_memory (
    conversation_id varchar(36) NOT NULL,
    content text NOT NULL,
    type varchar(10) NOT NULL,
    "timestamp" timestamp NOT NULL,
    sequence_id bigint NOT NULL,
    CONSTRAINT spring_ai_chat_memory_type_check
        CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'))
);

CREATE INDEX spring_ai_chat_memory_conversation_id_timestamp_idx
    ON public.spring_ai_chat_memory (conversation_id, "timestamp");

CREATE INDEX spring_ai_chat_memory_conversation_id_sequence_id_idx
    ON public.spring_ai_chat_memory (conversation_id, sequence_id);

CREATE TABLE public.assistant_conversations (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    title varchar(120) NOT NULL,
    last_activity_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT assistant_conversations_pkey PRIMARY KEY (id),
    CONSTRAINT assistant_conversation_title_check
        CHECK (length(btrim(title)) BETWEEN 1 AND 120),
    CONSTRAINT fk_assistant_conversation_actor
        FOREIGN KEY (actor_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE INDEX idx_assistant_conversation_actor_activity
    ON public.assistant_conversations (
        organization_id, actor_user_id, last_activity_at DESC, id
    );

CREATE TABLE public.assistant_conversation_messages (
    id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    role varchar(16) NOT NULL,
    content text NOT NULL,
    sequence_id bigint GENERATED ALWAYS AS IDENTITY,
    occurred_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT assistant_conversation_messages_pkey PRIMARY KEY (id),
    CONSTRAINT assistant_conversation_message_role_check
        CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT assistant_conversation_message_content_check
        CHECK (length(content) BETWEEN 1 AND 200000),
    CONSTRAINT fk_assistant_conversation_message_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES public.assistant_conversations(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_assistant_conversation_message_actor
        FOREIGN KEY (actor_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id)
);

CREATE UNIQUE INDEX idx_assistant_conversation_message_sequence
    ON public.assistant_conversation_messages (sequence_id);

CREATE INDEX idx_assistant_conversation_message_history
    ON public.assistant_conversation_messages (
        conversation_id, organization_id, actor_user_id, sequence_id
    );
