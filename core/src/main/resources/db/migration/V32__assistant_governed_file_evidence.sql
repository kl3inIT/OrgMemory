-- Bind governed Source revisions to one owned Assistant conversation, then
-- snapshot the ordered selection on the USER message that began a turn.

CREATE TABLE public.assistant_evidence_bindings (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    created_by_user_id uuid NOT NULL,
    source_object_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT assistant_evidence_bindings_pkey PRIMARY KEY (id),
    CONSTRAINT uq_assistant_evidence_binding_scope
        UNIQUE (id, organization_id, conversation_id, created_by_user_id),
    CONSTRAINT fk_assistant_evidence_binding_conversation
        FOREIGN KEY (conversation_id, organization_id, created_by_user_id)
        REFERENCES public.assistant_conversations(
            id, organization_id, actor_user_id
        )
        ON DELETE NO ACTION,
    CONSTRAINT fk_assistant_evidence_binding_source
        FOREIGN KEY (source_object_id, organization_id)
        REFERENCES public.source_objects(id, organization_id),
    CONSTRAINT fk_assistant_evidence_binding_revision
        FOREIGN KEY (source_revision_id, organization_id, source_object_id)
        REFERENCES public.source_revisions(id, organization_id, source_object_id)
);

CREATE INDEX idx_assistant_evidence_binding_conversation
    ON public.assistant_evidence_bindings (
        organization_id, created_by_user_id, conversation_id, created_at, id
    );

ALTER TABLE public.assistant_conversation_messages
    ADD CONSTRAINT uq_assistant_message_turn_scope_role
    UNIQUE (id, turn_id, conversation_id, organization_id, actor_user_id, role);

CREATE TABLE public.assistant_turn_evidence_bindings (
    id uuid NOT NULL,
    turn_id uuid NOT NULL,
    user_message_id uuid NOT NULL,
    user_message_role varchar(16) NOT NULL DEFAULT 'USER',
    binding_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    ordinal integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT assistant_turn_evidence_bindings_pkey PRIMARY KEY (id),
    CONSTRAINT chk_assistant_turn_evidence_ordinal CHECK (ordinal BETWEEN 1 AND 3),
    CONSTRAINT chk_assistant_turn_evidence_user_role CHECK (user_message_role = 'USER'),
    CONSTRAINT uq_assistant_turn_evidence_ordinal UNIQUE (turn_id, ordinal),
    CONSTRAINT uq_assistant_turn_evidence_binding UNIQUE (turn_id, binding_id),
    CONSTRAINT fk_assistant_turn_evidence_binding
        FOREIGN KEY (binding_id, organization_id, conversation_id, actor_user_id)
        REFERENCES public.assistant_evidence_bindings(
            id, organization_id, conversation_id, created_by_user_id
        )
        ON DELETE CASCADE,
    CONSTRAINT fk_assistant_turn_evidence_user_message
        FOREIGN KEY (
            user_message_id,
            turn_id,
            conversation_id,
            organization_id,
            actor_user_id,
            user_message_role
        )
        REFERENCES public.assistant_conversation_messages(
            id,
            turn_id,
            conversation_id,
            organization_id,
            actor_user_id,
            role
        )
        ON DELETE CASCADE
);

CREATE INDEX idx_assistant_turn_evidence_conversation
    ON public.assistant_turn_evidence_bindings (
        organization_id, actor_user_id, conversation_id, turn_id, ordinal
    );
