-- Actor-private Assistant files are deliberately separate from governed Sources.

CREATE TABLE public.assistant_files (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    file_name varchar(255) NOT NULL,
    media_type varchar(255) NOT NULL,
    content_length bigint NOT NULL,
    content_sha256 varchar(64) NOT NULL,
    object_key varchar(1024) NOT NULL,
    object_etag varchar(255),
    storage_version varchar(255),
    status varchar(32) NOT NULL,
    failure_code varchar(64),
    expires_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    cleanup_completed_at timestamp with time zone,
    processing_generation bigint NOT NULL DEFAULT 1,
    requested_profile_canonical text,
    requested_profile_sha256 varchar(64),
    resolved_profile_canonical text,
    resolved_profile_sha256 varchar(64),
    embedding_profile_id uuid,
    embedding_dimensions integer,
    processing_attempt integer NOT NULL DEFAULT 0,
    claim_owner varchar(255),
    lease_expires_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT assistant_files_pkey PRIMARY KEY (id),
    CONSTRAINT uq_assistant_file_scope UNIQUE (id, organization_id, actor_user_id),
    CONSTRAINT uq_assistant_file_generation_scope UNIQUE (
        id, organization_id, actor_user_id, processing_generation
    ),
    CONSTRAINT uq_assistant_file_object_key UNIQUE (object_key),
    CONSTRAINT chk_assistant_file_length CHECK (content_length > 0),
    CONSTRAINT chk_assistant_file_sha CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_assistant_file_status CHECK (status IN (
        'UPLOADED', 'PROCESSING', 'READY', 'FAILED', 'DELETING', 'DELETED', 'EXPIRED'
    )),
    CONSTRAINT chk_assistant_file_generation CHECK (processing_generation > 0),
    CONSTRAINT chk_assistant_file_attempt CHECK (processing_attempt >= 0),
    CONSTRAINT chk_assistant_file_embedding CHECK (
        (embedding_profile_id IS NULL AND embedding_dimensions IS NULL)
        OR (embedding_profile_id IS NOT NULL AND embedding_dimensions BETWEEN 1 AND 16000)
    ),
    CONSTRAINT chk_assistant_file_requested_profile CHECK (
        (requested_profile_canonical IS NULL AND requested_profile_sha256 IS NULL)
        OR (requested_profile_canonical IS NOT NULL AND requested_profile_sha256 ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT chk_assistant_file_resolved_profile CHECK (
        (resolved_profile_canonical IS NULL AND resolved_profile_sha256 IS NULL)
        OR (resolved_profile_canonical IS NOT NULL AND resolved_profile_sha256 ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT fk_assistant_file_actor
        FOREIGN KEY (actor_user_id, organization_id)
        REFERENCES public.app_users(id, organization_id),
    CONSTRAINT fk_assistant_file_organization
        FOREIGN KEY (organization_id) REFERENCES public.organizations(id),
    CONSTRAINT fk_assistant_file_embedding_profile
        FOREIGN KEY (embedding_profile_id, organization_id, embedding_dimensions)
        REFERENCES public.embedding_profiles(id, organization_id, dimensions)
);

CREATE INDEX idx_assistant_file_recent
    ON public.assistant_files (
        organization_id, actor_user_id, created_at DESC, id DESC
    )
    WHERE status NOT IN ('DELETED', 'EXPIRED');

CREATE INDEX idx_assistant_file_processing
    ON public.assistant_files (status, lease_expires_at, created_at, id)
    WHERE status IN ('UPLOADED', 'PROCESSING', 'DELETING', 'EXPIRED');

CREATE INDEX idx_assistant_file_expiry
    ON public.assistant_files (expires_at, id)
    WHERE status NOT IN ('DELETING', 'DELETED', 'EXPIRED');

CREATE TABLE public.assistant_file_chunks (
    id uuid NOT NULL,
    assistant_file_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    processing_generation bigint NOT NULL,
    chunk_index integer NOT NULL,
    content text NOT NULL,
    heading varchar(1024),
    start_page integer,
    end_page integer,
    token_count integer NOT NULL,
    source_start_char integer,
    source_end_char integer,
    source_block_indexes integer[] NOT NULL DEFAULT '{}',
    canonical_text_sha256 varchar(64) NOT NULL,
    embedding vector NOT NULL,
    embedding_dimensions integer NOT NULL,
    embedding_profile_id uuid NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(heading, '') || ' ' || content)
    ) STORED,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT assistant_file_chunks_pkey PRIMARY KEY (id),
    CONSTRAINT uq_assistant_file_chunk_scope
        UNIQUE (id, organization_id, actor_user_id, assistant_file_id, processing_generation),
    CONSTRAINT uq_assistant_file_chunk_index
        UNIQUE (assistant_file_id, processing_generation, chunk_index),
    CONSTRAINT chk_assistant_file_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT chk_assistant_file_chunk_content CHECK (btrim(content) <> ''),
    CONSTRAINT chk_assistant_file_chunk_tokens CHECK (token_count > 0),
    CONSTRAINT chk_assistant_file_chunk_dimensions CHECK (embedding_dimensions BETWEEN 1 AND 16000),
    CONSTRAINT chk_assistant_file_chunk_generation CHECK (processing_generation > 0),
    CONSTRAINT chk_assistant_file_chunk_sha CHECK (canonical_text_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT fk_assistant_file_chunk_file
        FOREIGN KEY (assistant_file_id, organization_id, actor_user_id)
        REFERENCES public.assistant_files(id, organization_id, actor_user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_assistant_file_chunk_embedding_profile
        FOREIGN KEY (embedding_profile_id, organization_id, embedding_dimensions)
        REFERENCES public.embedding_profiles(id, organization_id, dimensions)
);

CREATE INDEX idx_assistant_file_chunk_search
    ON public.assistant_file_chunks USING gin (search_vector);

CREATE INDEX idx_assistant_file_chunk_embedding_1536_hnsw
    ON public.assistant_file_chunks USING hnsw (
        (embedding::vector(1536)) vector_cosine_ops
    )
    WHERE embedding_dimensions = 1536;

CREATE TABLE public.assistant_turn_files (
    id uuid NOT NULL,
    turn_id uuid NOT NULL,
    user_message_id uuid NOT NULL,
    user_message_role varchar(16) NOT NULL DEFAULT 'USER',
    assistant_file_id uuid NOT NULL,
    processing_generation bigint NOT NULL,
    organization_id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    ordinal integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT assistant_turn_files_pkey PRIMARY KEY (id),
    CONSTRAINT chk_assistant_turn_file_ordinal CHECK (ordinal BETWEEN 1 AND 3),
    CONSTRAINT chk_assistant_turn_file_role CHECK (user_message_role = 'USER'),
    CONSTRAINT chk_assistant_turn_file_generation CHECK (processing_generation > 0),
    CONSTRAINT uq_assistant_turn_file_ordinal UNIQUE (turn_id, ordinal),
    CONSTRAINT uq_assistant_turn_file UNIQUE (turn_id, assistant_file_id),
    CONSTRAINT fk_assistant_turn_file_file
        FOREIGN KEY (
            assistant_file_id, organization_id, actor_user_id,
            processing_generation
        )
        REFERENCES public.assistant_files(
            id, organization_id, actor_user_id, processing_generation
        ),
    CONSTRAINT fk_assistant_turn_file_message
        FOREIGN KEY (
            user_message_id, turn_id, conversation_id,
            organization_id, actor_user_id, user_message_role
        )
        REFERENCES public.assistant_conversation_messages(
            id, turn_id, conversation_id, organization_id, actor_user_id, role
        )
        ON DELETE CASCADE
);

CREATE INDEX idx_assistant_turn_file_conversation
    ON public.assistant_turn_files (
        organization_id, actor_user_id, conversation_id, turn_id, ordinal
    );

ALTER TABLE public.assistant_message_citations
    ADD COLUMN evidence_kind varchar(32) NOT NULL DEFAULT 'KNOWLEDGE',
    ADD COLUMN assistant_file_id uuid,
    ADD COLUMN processing_generation bigint;

ALTER TABLE public.assistant_message_citations
    ADD CONSTRAINT chk_assistant_message_citation_kind CHECK (
        (evidence_kind = 'KNOWLEDGE'
            AND assistant_file_id IS NULL
            AND processing_generation IS NULL)
        OR
        (evidence_kind = 'ASSISTANT_FILE'
            AND assistant_file_id IS NOT NULL
            AND processing_generation > 0)
    ),
    ADD CONSTRAINT fk_assistant_message_citation_private_file
        FOREIGN KEY (
            assistant_file_id, organization_id, actor_user_id,
            processing_generation
        )
        REFERENCES public.assistant_files(
            id, organization_id, actor_user_id, processing_generation
        );

CREATE INDEX idx_assistant_message_private_citation
    ON public.assistant_message_citations (
        organization_id, actor_user_id, assistant_file_id, processing_generation
    )
    WHERE evidence_kind = 'ASSISTANT_FILE';
