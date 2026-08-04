CREATE TABLE public.assistant_message_citations (
    id uuid NOT NULL,
    message_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    citation_number integer NOT NULL,
    chunk_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT assistant_message_citations_pkey PRIMARY KEY (id),
    CONSTRAINT chk_assistant_message_citation_number
        CHECK (citation_number BETWEEN 1 AND 100),
    CONSTRAINT uq_assistant_message_citation_number
        UNIQUE (message_id, citation_number),
    CONSTRAINT fk_assistant_message_citation_message
        FOREIGN KEY (message_id, organization_id, actor_user_id)
        REFERENCES public.assistant_conversation_messages(
            id, organization_id, actor_user_id
        )
        ON DELETE CASCADE
);

CREATE INDEX idx_assistant_message_citation_owner
    ON public.assistant_message_citations (
        organization_id, actor_user_id, message_id, citation_number
    );

COMMENT ON TABLE public.assistant_message_citations IS
    'Minimal Assistant-owned citation number to canonical chunk reference. Current authorization is always rechecked before the chunk id leaves the service boundary.';
