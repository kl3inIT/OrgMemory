CREATE EXTENSION IF NOT EXISTS vector;

--
-- PostgreSQL database dump
--


-- Dumped from database version 18.4 (Debian 18.4-1.pgdg13+1)
-- Dumped by pg_dump version 18.4 (Debian 18.4-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--



--
--



--
-- Name: reject_entry_insert_into_sealed_acl(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reject_entry_insert_into_sealed_acl() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM source_acl_snapshot_seals seal
        WHERE seal.source_acl_snapshot_id = NEW.source_acl_snapshot_id
          AND seal.organization_id = NEW.organization_id
    ) THEN
        RAISE EXCEPTION 'source ACL snapshot is sealed'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: reject_permission_audit_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reject_permission_audit_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'permission_audit_events is append-only'
        USING ERRCODE = '55000';
END;
$$;


--
-- Name: reject_source_acl_evidence_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reject_source_acl_evidence_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'source ACL evidence is append-only'
        USING ERRCODE = '55000';
END;
$$;


--
-- Name: set_graph_entity_contribution_search_vector(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_graph_entity_contribution_search_vector() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.search_vector := to_tsvector(
        'simple',
        coalesce(NEW.entity_type, '') || ' ' || coalesce(NEW.description, '')
    );
    RETURN NEW;
END;
$$;


--
-- Name: set_graph_entity_identity_search_vector(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_graph_entity_identity_search_vector() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.search_vector := to_tsvector(
        'simple',
        coalesce(NEW.normalized_name, '')
    );
    RETURN NEW;
END;
$$;


--
-- Name: set_graph_relation_contribution_search_vector(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_graph_relation_contribution_search_vector() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.search_vector := to_tsvector(
        'simple',
        coalesce(NEW.relation_type, '')
            || ' '
            || coalesce(NEW.search_content, '')
    );
    RETURN NEW;
END;
$$;


--
-- Name: validate_source_acl_seal(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_source_acl_seal() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    actual_entry_count integer;
BEGIN
    SELECT count(*)
    INTO actual_entry_count
    FROM source_acl_entries entry
    WHERE entry.source_acl_snapshot_id = NEW.source_acl_snapshot_id
      AND entry.organization_id = NEW.organization_id;

    IF actual_entry_count <> NEW.entry_count THEN
        RAISE EXCEPTION 'source ACL seal entry count does not match snapshot entries'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_users (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    department_id uuid,
    name character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    role character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    active boolean DEFAULT true NOT NULL
);


--
-- Name: connector_crawl_attempts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.connector_crawl_attempts (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_system character varying(64) NOT NULL,
    source_connection_key character varying(128) NOT NULL,
    crawl_cursor character varying(512),
    outcome character varying(32) NOT NULL,
    objects_materialized integer NOT NULL,
    objects_rotated integer NOT NULL,
    objects_rematerialized integer NOT NULL,
    objects_retired integer NOT NULL,
    objects_failed integer NOT NULL,
    error_code character varying(64),
    error_message character varying(512),
    attempted_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_connector_crawl_attempt_counts CHECK (((objects_materialized >= 0) AND (objects_rotated >= 0) AND (objects_rematerialized >= 0) AND (objects_retired >= 0) AND (objects_failed >= 0))),
    CONSTRAINT chk_connector_crawl_attempt_cursor CHECK (((((outcome)::text = 'UNAVAILABLE'::text) AND (crawl_cursor IS NULL)) OR (((outcome)::text <> 'UNAVAILABLE'::text) AND (crawl_cursor IS NOT NULL)))),
    CONSTRAINT chk_connector_crawl_attempt_nonblank CHECK (((btrim((source_system)::text) <> ''::text) AND (btrim((source_connection_key)::text) <> ''::text) AND ((crawl_cursor IS NULL) OR (btrim((crawl_cursor)::text) <> ''::text)))),
    CONSTRAINT chk_connector_crawl_attempt_outcome CHECK (((outcome)::text = ANY ((ARRAY['SUCCEEDED'::character varying, 'REJECTED'::character varying, 'FAILED'::character varying, 'UNAVAILABLE'::character varying])::text[]))),
    CONSTRAINT chk_connector_crawl_attempt_reason CHECK (((((outcome)::text = 'SUCCEEDED'::text) AND (error_code IS NULL) AND (error_message IS NULL)) OR (((outcome)::text <> 'SUCCEEDED'::text) AND (btrim((error_message)::text) <> ''::text))))
);


--
-- Name: TABLE connector_crawl_attempts; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.connector_crawl_attempts IS 'One row per crawl batch a driver acted on, kept so an administrator can see why a connection is producing nothing without reading worker logs. error_message carries a diagnostic only: the credential travels in an Authorization header and never appears in an adapter exception message, and nothing here may be allowed to change that.';


--
-- Name: connector_crawl_checkpoints; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.connector_crawl_checkpoints (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_system character varying(64) NOT NULL,
    source_connection_key character varying(128) NOT NULL,
    crawl_cursor character varying(512) NOT NULL,
    checkpointed_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_connector_checkpoint_nonblank CHECK (((btrim((source_system)::text) <> ''::text) AND (btrim((source_connection_key)::text) <> ''::text) AND (btrim((crawl_cursor)::text) <> ''::text)))
);


--
-- Name: TABLE connector_crawl_checkpoints; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.connector_crawl_checkpoints IS 'Last crawl cursor a connection completed, so an interrupted or restarted driver resumes instead of replaying. One row per connection; the cursor is opaque to OrgMemory.';


--
-- Name: departments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.departments (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: embedding_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.embedding_profiles (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    profile_key character varying(255) NOT NULL,
    provider character varying(64) NOT NULL,
    model character varying(128) NOT NULL,
    dimensions integer NOT NULL,
    distance_metric character varying(32) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_embedding_profile_dimensions CHECK (((dimensions > 0) AND (dimensions <= 16000))),
    CONSTRAINT chk_embedding_profile_distance_metric CHECK (((distance_metric)::text = 'COSINE'::text)),
    CONSTRAINT chk_embedding_profile_nonblank CHECK (((btrim((profile_key)::text) <> ''::text) AND (btrim((provider)::text) <> ''::text) AND (btrim((model)::text) <> ''::text)))
);


--
-- Name: evidence_blobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evidence_blobs (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    object_key character varying(1024) NOT NULL,
    media_type character varying(255) NOT NULL,
    content_length bigint NOT NULL,
    content_sha256 character varying(64) NOT NULL,
    etag character varying(255),
    storage_version character varying(255),
    scan_status character varying(32) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_evidence_blob_length CHECK ((content_length > 0)),
    CONSTRAINT chk_evidence_blob_nonblank CHECK (((btrim((object_key)::text) <> ''::text) AND (btrim((media_type)::text) <> ''::text))),
    CONSTRAINT chk_evidence_blob_scan_status CHECK (((scan_status)::text = ANY ((ARRAY['PENDING'::character varying, 'BASIC_VALIDATED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT chk_evidence_blob_sha CHECK (((content_sha256)::text ~ '^[0-9a-f]{64}$'::text))
);


--
-- Name: external_identities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.external_identities (
    id uuid NOT NULL,
    app_user_id uuid NOT NULL,
    issuer character varying(512) NOT NULL,
    subject character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: graph_curation_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graph_curation_records (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    workspace character varying(255) NOT NULL,
    collection_name character varying(255) NOT NULL,
    curation_kind character varying(32) NOT NULL,
    identity_kind character varying(16),
    identity_id uuid,
    target_identity_id uuid,
    source_entity_id uuid,
    target_entity_id uuid,
    identity_name text,
    contribution_type character varying(255),
    keywords text,
    description text,
    weight double precision,
    governing_knowledge_asset_id uuid,
    governing_source_revision_id uuid,
    governing_chunk_id uuid,
    governing_acl_snapshot_id uuid,
    governing_acl_generation bigint,
    actor_user_id uuid NOT NULL,
    authorization_model_id character varying(255) NOT NULL,
    curation_acl_generation bigint NOT NULL,
    curated_at timestamp with time zone NOT NULL,
    reason text NOT NULL,
    idempotency_key character varying(255) NOT NULL,
    content_fingerprint character varying(64) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    deactivated_by_user_id uuid,
    deactivated_at timestamp with time zone,
    deactivation_reason text,
    CONSTRAINT chk_graph_curation_acl_generation CHECK ((curation_acl_generation >= 0)),
    CONSTRAINT chk_graph_curation_deactivation CHECK (((active AND (deactivated_by_user_id IS NULL) AND (deactivated_at IS NULL) AND (deactivation_reason IS NULL)) OR ((NOT active) AND (deactivated_by_user_id IS NOT NULL) AND (deactivated_at IS NOT NULL) AND (deactivation_reason IS NOT NULL)))),
    CONSTRAINT chk_graph_curation_fingerprint CHECK (((content_fingerprint)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT chk_graph_curation_identity_kind CHECK (((identity_kind IS NULL) OR ((identity_kind)::text = ANY ((ARRAY['ENTITY'::character varying, 'RELATION'::character varying])::text[])))),
    CONSTRAINT chk_graph_curation_kind CHECK (((curation_kind)::text = ANY ((ARRAY['CURATED_ENTITY'::character varying, 'CURATED_RELATION'::character varying, 'IDENTITY_ALIAS'::character varying, 'IDENTITY_SUPPRESSION'::character varying])::text[]))),
    CONSTRAINT chk_graph_curation_weight CHECK (((weight IS NULL) OR (weight > (0)::double precision)))
);


--
-- Name: graph_processing_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graph_processing_profiles (
    id uuid NOT NULL,
    canonical_sha256 character varying(64) NOT NULL,
    canonical_form text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_graph_processing_profile_canonical_nonblank CHECK ((btrim(canonical_form) <> ''::text)),
    CONSTRAINT chk_graph_processing_profile_sha CHECK (((canonical_sha256)::text ~ '^[0-9a-f]{64}$'::text))
);


--
-- Name: graph_index_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graph_index_jobs (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    knowledge_asset_version_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    graph_processing_profile_id uuid NOT NULL,
    projection_generation bigint NOT NULL,
    job_type character varying(64) NOT NULL,
    status character varying(32) NOT NULL,
    available_at timestamp with time zone NOT NULL,
    lease_owner character varying(128),
    lease_until timestamp with time zone,
    attempt_count integer NOT NULL,
    max_attempts integer NOT NULL,
    last_error_code character varying(64),
    last_error_message character varying(512),
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    idempotency_key character varying(255) NOT NULL,
    manifest_fingerprint character varying(64),
    cancellation_requested boolean DEFAULT false NOT NULL,
    cancellation_requested_at timestamp with time zone,
    CONSTRAINT chk_graph_index_job_attempts CHECK (((attempt_count >= 0) AND (max_attempts > 0) AND (attempt_count <= max_attempts))),
    CONSTRAINT chk_graph_index_job_cancellation CHECK (((cancellation_requested AND (cancellation_requested_at IS NOT NULL)) OR ((NOT cancellation_requested) AND (cancellation_requested_at IS NULL)))),
    CONSTRAINT chk_graph_index_job_completion CHECK (((((status)::text = ANY ((ARRAY['SUCCEEDED'::character varying, 'FAILED'::character varying, 'SUPERSEDED'::character varying, 'CANCELLED'::character varying])::text[])) AND (completed_at IS NOT NULL)) OR (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying])::text[])) AND (completed_at IS NULL)))),
    CONSTRAINT chk_graph_index_job_generation CHECK ((projection_generation > 0)),
    CONSTRAINT chk_graph_index_job_lease CHECK (((((status)::text = 'PROCESSING'::text) AND (lease_owner IS NOT NULL) AND (lease_until IS NOT NULL)) OR (((status)::text <> 'PROCESSING'::text) AND (lease_owner IS NULL) AND (lease_until IS NULL)))),
    CONSTRAINT chk_graph_index_job_manifest CHECK (((manifest_fingerprint IS NULL) OR ((manifest_fingerprint)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT chk_graph_index_job_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying, 'SUPERSEDED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT chk_graph_index_job_type CHECK (((job_type)::text = 'INDEX_KNOWLEDGE_ASSET_VERSION'::text))
);


--
-- Name: graph_model_invocation_cache; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graph_model_invocation_cache (
    organization_id uuid NOT NULL,
    workspace character varying(255) NOT NULL,
    collection_name character varying(255) NOT NULL,
    operation character varying(64) NOT NULL,
    input_hash character varying(64) NOT NULL,
    model_route_fingerprint character varying(255) NOT NULL,
    profile_fingerprint character varying(255) NOT NULL,
    media_type character varying(255) NOT NULL,
    payload text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_graph_model_cache_expiry CHECK ((expires_at > created_at)),
    CONSTRAINT chk_graph_model_cache_hash CHECK (((input_hash)::text ~ '^[0-9a-f]{64}$'::text))
);


--
-- Name: graph_retrieval_cache_evidence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graph_retrieval_cache_evidence (
    cache_entry_id uuid NOT NULL,
    ordinal integer NOT NULL,
    organization_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    chunk_id uuid,
    acl_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    CONSTRAINT chk_graph_cache_evidence_acl_generation CHECK ((acl_generation >= 0)),
    CONSTRAINT chk_graph_cache_evidence_ordinal CHECK ((ordinal >= 0))
);


--
-- Name: graph_retrieval_result_cache; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.graph_retrieval_result_cache (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    workspace character varying(255) NOT NULL,
    collection_name character varying(255) NOT NULL,
    publication_batch_id uuid NOT NULL,
    publication_generation bigint NOT NULL,
    publication_manifest_fingerprint character varying(255) CONSTRAINT graph_retrieval_result_cach_publication_manifest_finge_not_null NOT NULL,
    publication_kinds character varying(255) NOT NULL,
    authorization_fingerprint character varying(64) NOT NULL,
    query_hash character varying(64) NOT NULL,
    strategy character varying(64) NOT NULL,
    model_route_fingerprint character varying(255) NOT NULL,
    media_type character varying(255) NOT NULL,
    payload text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_graph_retrieval_cache_authorization CHECK (((authorization_fingerprint)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT chk_graph_retrieval_cache_expiry CHECK ((expires_at > created_at)),
    CONSTRAINT chk_graph_retrieval_cache_generation CHECK ((publication_generation >= 0)),
    CONSTRAINT chk_graph_retrieval_cache_query CHECK (((query_hash)::text ~ '^[0-9a-f]{64}$'::text))
);


--
-- Name: knowledge_asset_evidence_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.knowledge_asset_evidence_links (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    knowledge_asset_version_id uuid CONSTRAINT knowledge_asset_evidence_li_knowledge_asset_version_id_not_null NOT NULL,
    source_revision_id uuid NOT NULL,
    source_acl_snapshot_id uuid NOT NULL,
    evidence_role character varying(32) NOT NULL,
    span_start integer,
    span_end integer,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_knowledge_asset_evidence_role CHECK (((evidence_role)::text = ANY ((ARRAY['PRIMARY'::character varying, 'SUPPORTING'::character varying])::text[]))),
    CONSTRAINT chk_knowledge_asset_evidence_span CHECK ((((span_start IS NULL) AND (span_end IS NULL)) OR ((span_start IS NOT NULL) AND (span_end IS NOT NULL) AND (span_start >= 0) AND (span_end > span_start))))
);


--
-- Name: knowledge_asset_publication_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.knowledge_asset_publication_outbox (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    source_object_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    owner_user_id uuid NOT NULL,
    projection_generation bigint CONSTRAINT knowledge_asset_publication_outb_projection_generation_not_null NOT NULL,
    embedding_profile_id uuid CONSTRAINT knowledge_asset_publication_outbo_embedding_profile_id_not_null NOT NULL,
    embedding_dimensions integer CONSTRAINT knowledge_asset_publication_outbo_embedding_dimensions_not_null NOT NULL,
    pipeline_version character varying(64) NOT NULL,
    status character varying(32) NOT NULL,
    attempt_count integer NOT NULL,
    authorization_model_id character varying(255),
    last_error_code character varying(64),
    last_error_message character varying(512),
    applied_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    knowledge_space_id uuid NOT NULL,
    knowledge_asset_version_id uuid CONSTRAINT knowledge_asset_publication_knowledge_asset_version_id_not_null NOT NULL,
    CONSTRAINT chk_knowledge_asset_publication_applied CHECK (((((status)::text = 'PENDING'::text) AND (authorization_model_id IS NULL) AND (applied_at IS NULL)) OR (((status)::text = 'APPLIED'::text) AND (authorization_model_id IS NOT NULL) AND (applied_at IS NOT NULL)))),
    CONSTRAINT chk_knowledge_asset_publication_attempts CHECK ((attempt_count >= 0)),
    CONSTRAINT chk_knowledge_asset_publication_generation CHECK ((projection_generation > 0)),
    CONSTRAINT chk_knowledge_asset_publication_profile CHECK (((embedding_dimensions > 0) AND (btrim((pipeline_version)::text) <> ''::text))),
    CONSTRAINT chk_knowledge_asset_publication_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPLIED'::character varying])::text[])))
);


--
-- Name: knowledge_asset_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.knowledge_asset_versions (
    id uuid CONSTRAINT knowledge_assets_id_not_null NOT NULL,
    organization_id uuid CONSTRAINT knowledge_assets_organization_id_not_null NOT NULL,
    raw_source_object_id uuid CONSTRAINT knowledge_assets_raw_source_object_id_not_null NOT NULL,
    normalized_record_id uuid CONSTRAINT knowledge_assets_normalized_record_id_not_null NOT NULL,
    source_acl_snapshot_id uuid CONSTRAINT knowledge_assets_source_acl_snapshot_id_not_null NOT NULL,
    department_id uuid,
    title character varying(255) CONSTRAINT knowledge_assets_title_not_null NOT NULL,
    content text CONSTRAINT knowledge_assets_content_not_null NOT NULL,
    language character varying(16),
    classification character varying(32) CONSTRAINT knowledge_assets_classification_not_null NOT NULL,
    declared_access character varying(32) CONSTRAINT knowledge_assets_declared_access_not_null NOT NULL,
    content_sha256 character varying(64) CONSTRAINT knowledge_assets_content_sha256_not_null NOT NULL,
    orgmemory_gate character varying(16) CONSTRAINT knowledge_assets_orgmemory_gate_not_null NOT NULL,
    status character varying(32) CONSTRAINT knowledge_assets_status_not_null NOT NULL,
    activated_at timestamp with time zone,
    retired_at timestamp with time zone,
    created_at timestamp with time zone CONSTRAINT knowledge_assets_created_at_not_null NOT NULL,
    updated_at timestamp with time zone CONSTRAINT knowledge_assets_updated_at_not_null NOT NULL,
    version bigint CONSTRAINT knowledge_assets_version_not_null NOT NULL,
    knowledge_space_id uuid CONSTRAINT knowledge_assets_knowledge_space_id_not_null NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    version_number bigint NOT NULL,
    source_revision_id uuid,
    CONSTRAINT chk_knowledge_asset_version_number CHECK ((version_number > 0)),
    CONSTRAINT chk_knowledge_classification_access CHECK (((((classification)::text = 'PUBLIC'::text) AND ((declared_access)::text = 'ALL'::text)) OR (((classification)::text = 'INTERNAL'::text) AND ((declared_access)::text = 'ALL_EMPLOYEES'::text)) OR (((classification)::text = 'CONFIDENTIAL'::text) AND ((declared_access)::text = 'OWN_DEPARTMENT'::text)) OR (((classification)::text = 'RESTRICTED'::text) AND ((declared_access)::text = 'EXECUTIVE_ONLY'::text)))),
    CONSTRAINT chk_knowledge_confidential_department CHECK ((((classification)::text <> 'CONFIDENTIAL'::text) OR (department_id IS NOT NULL))),
    CONSTRAINT chk_knowledge_content_sha CHECK (((content_sha256)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT chk_knowledge_lifecycle CHECK (((((status)::text = 'PENDING'::text) AND (activated_at IS NULL) AND (retired_at IS NULL)) OR (((status)::text = 'ACTIVE'::text) AND (activated_at IS NOT NULL) AND (retired_at IS NULL)) OR (((status)::text = 'RETIRED'::text) AND (activated_at IS NOT NULL) AND (retired_at IS NOT NULL)))),
    CONSTRAINT chk_knowledge_nonblank CHECK (((btrim((title)::text) <> ''::text) AND (btrim(content) <> ''::text))),
    CONSTRAINT chk_knowledge_orgmemory_gate CHECK (((orgmemory_gate)::text = ANY ((ARRAY['ALLOW'::character varying, 'DENY'::character varying, 'UNKNOWN'::character varying])::text[]))),
    CONSTRAINT chk_knowledge_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVE'::character varying, 'RETIRED'::character varying])::text[])))
);


--
-- Name: knowledge_assets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.knowledge_assets (
    id uuid CONSTRAINT knowledge_assets_id_not_null1 NOT NULL,
    organization_id uuid CONSTRAINT knowledge_assets_organization_id_not_null1 NOT NULL,
    knowledge_space_id uuid CONSTRAINT knowledge_assets_knowledge_space_id_not_null1 NOT NULL,
    source_object_id uuid,
    current_version_id uuid,
    archived_at timestamp with time zone,
    created_at timestamp with time zone CONSTRAINT knowledge_assets_created_at_not_null1 NOT NULL,
    updated_at timestamp with time zone CONSTRAINT knowledge_assets_updated_at_not_null1 NOT NULL,
    version bigint CONSTRAINT knowledge_assets_version_not_null1 NOT NULL,
    CONSTRAINT chk_knowledge_asset_archive_head CHECK (((archived_at IS NULL) OR (current_version_id IS NULL)))
);


--
-- Name: knowledge_chunks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.knowledge_chunks (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_object_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    chunk_index integer NOT NULL,
    content text NOT NULL,
    content_sha256 character varying(64) NOT NULL,
    token_count integer,
    start_page integer,
    end_page integer,
    heading character varying(512),
    embedding public.vector NOT NULL,
    embedding_profile_id uuid NOT NULL,
    embedding_dimensions integer NOT NULL,
    pipeline_version character varying(64) NOT NULL,
    projection_generation bigint NOT NULL,
    active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple'::regconfig, (((COALESCE(heading, ''::character varying))::text || ' '::text) || content))) STORED,
    knowledge_asset_version_id uuid NOT NULL,
    source_start_char integer,
    source_end_char integer,
    source_block_indexes integer[] DEFAULT '{}'::integer[] NOT NULL,
    canonical_text_sha256 character varying(64),
    CONSTRAINT chk_knowledge_chunk_block_indexes CHECK ((array_position(source_block_indexes, NULL::integer) IS NULL)),
    CONSTRAINT chk_knowledge_chunk_content CHECK ((btrim(content) <> ''::text)),
    CONSTRAINT chk_knowledge_chunk_dimensions CHECK (((embedding_dimensions > 0) AND (embedding_dimensions <= 16000))),
    CONSTRAINT chk_knowledge_chunk_generation CHECK ((projection_generation > 0)),
    CONSTRAINT chk_knowledge_chunk_index CHECK ((chunk_index >= 0)),
    CONSTRAINT chk_knowledge_chunk_sha CHECK (((content_sha256)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT chk_knowledge_chunk_source_span CHECK ((((source_start_char IS NULL) AND (source_end_char IS NULL) AND (canonical_text_sha256 IS NULL)) OR ((source_start_char >= 0) AND (source_end_char > source_start_char) AND ((canonical_text_sha256)::text ~ '^[0-9a-f]{64}$'::text))))
);


--
-- Name: COLUMN knowledge_chunks.source_start_char; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.knowledge_chunks.source_start_char IS 'Inclusive UTF-16 character offset into the canonical normalized document.';


--
-- Name: COLUMN knowledge_chunks.source_end_char; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.knowledge_chunks.source_end_char IS 'Exclusive UTF-16 character offset into the canonical normalized document.';


--
-- Name: knowledge_spaces; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.knowledge_spaces (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    department_id uuid,
    space_key character varying(128) NOT NULL,
    name character varying(255) NOT NULL,
    active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_knowledge_space_nonblank CHECK (((btrim((space_key)::text) <> ''::text) AND (btrim((name)::text) <> ''::text)))
);


--
-- Name: normalized_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.normalized_records (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    raw_source_object_id uuid NOT NULL,
    source_acl_snapshot_id uuid NOT NULL,
    normalizer_version character varying(64) NOT NULL,
    title character varying(255),
    normalized_content text,
    language character varying(16),
    department_id uuid,
    classification character varying(32),
    declared_access character varying(32),
    content_sha256 character varying(64),
    status character varying(32) NOT NULL,
    issue_code character varying(64),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_normalized_classification_access CHECK ((((status)::text <> ALL ((ARRAY['READY'::character varying, 'PROMOTED'::character varying])::text[])) OR ((((classification)::text = 'PUBLIC'::text) AND ((declared_access)::text = 'ALL'::text)) OR (((classification)::text = 'INTERNAL'::text) AND ((declared_access)::text = 'ALL_EMPLOYEES'::text)) OR (((classification)::text = 'CONFIDENTIAL'::text) AND ((declared_access)::text = 'OWN_DEPARTMENT'::text)) OR (((classification)::text = 'RESTRICTED'::text) AND ((declared_access)::text = 'EXECUTIVE_ONLY'::text))))),
    CONSTRAINT chk_normalized_confidential_department CHECK ((((status)::text <> ALL ((ARRAY['READY'::character varying, 'PROMOTED'::character varying])::text[])) OR ((classification)::text <> 'CONFIDENTIAL'::text) OR (department_id IS NOT NULL))),
    CONSTRAINT chk_normalized_content_sha CHECK (((content_sha256 IS NULL) OR ((content_sha256)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT chk_normalized_issue CHECK (((issue_code IS NULL) OR ((issue_code)::text = ANY ((ARRAY['CONTENT_EMPTY'::character varying, 'CLASSIFICATION_MISSING'::character varying, 'DECLARED_ACCESS_MISSING'::character varying, 'DECLARED_ACCESS_MISMATCH'::character varying, 'DEPARTMENT_MISSING'::character varying, 'ACL_NOT_COMPLETE'::character varying])::text[])))),
    CONSTRAINT chk_normalized_ready_fields CHECK (((((status)::text = ANY ((ARRAY['READY'::character varying, 'PROMOTED'::character varying])::text[])) AND (title IS NOT NULL) AND (btrim((title)::text) <> ''::text) AND (normalized_content IS NOT NULL) AND (btrim(normalized_content) <> ''::text) AND (content_sha256 IS NOT NULL) AND (classification IS NOT NULL) AND (declared_access IS NOT NULL) AND (issue_code IS NULL)) OR (((status)::text = 'QUARANTINED'::text) AND (issue_code IS NOT NULL)) OR ((status)::text = 'REJECTED'::text))),
    CONSTRAINT chk_normalized_status CHECK (((status)::text = ANY ((ARRAY['READY'::character varying, 'QUARANTINED'::character varying, 'PROMOTED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT chk_normalized_version CHECK ((btrim((normalizer_version)::text) <> ''::text))
);


--
-- Name: organizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organizations (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: permission_audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permission_audit_events (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    actor_user_id uuid,
    operation character varying(64) NOT NULL,
    resource_type character varying(64) NOT NULL,
    resource_id character varying(255) NOT NULL,
    decision character varying(16) NOT NULL,
    reason_code character varying(128) NOT NULL,
    policy_version character varying(64) NOT NULL,
    request_id character varying(128),
    query_fingerprint character varying(64),
    metadata_json text,
    occurred_at timestamp with time zone NOT NULL,
    ingestion_acl_snapshot_id uuid,
    current_acl_snapshot_id uuid,
    authorization_model_id character varying(255),
    source_revision_id uuid,
    knowledge_chunk_id uuid,
    embedding_profile_id uuid,
    projection_generation bigint,
    CONSTRAINT chk_permission_audit_metadata_json_null CHECK ((metadata_json IS NULL)),
    CONSTRAINT chk_permission_audit_projection_generation CHECK (((projection_generation IS NULL) OR (projection_generation > 0))),
    CONSTRAINT permission_audit_events_decision_check CHECK (((decision)::text = ANY ((ARRAY['ALLOW'::character varying, 'DENY'::character varying])::text[])))
);


--
-- Name: COLUMN permission_audit_events.metadata_json; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.permission_audit_events.metadata_json IS 'Reserved. Free-form audit metadata is disabled until a structured allowlist is defined.';


--
-- Name: projection_batch_receipts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projection_batch_receipts (
    batch_id uuid NOT NULL,
    projection_kind character varying(32) NOT NULL,
    prepared_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_projection_receipt_kind CHECK (((projection_kind)::text = ANY ((ARRAY['CONTENT'::character varying, 'LEXICAL'::character varying, 'VECTOR'::character varying, 'GRAPH'::character varying])::text[])))
);


--
-- Name: projection_batches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projection_batches (
    batch_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    workspace character varying(255) NOT NULL,
    collection_name character varying(255) NOT NULL,
    expected_previous_generation bigint NOT NULL,
    generation bigint NOT NULL,
    idempotency_key character varying(255) NOT NULL,
    manifest_fingerprint character varying(255) NOT NULL,
    required_projections text NOT NULL,
    status character varying(16) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    aborted_at timestamp with time zone,
    abort_reason text,
    CONSTRAINT chk_projection_batch_completion CHECK (((((status)::text = 'PREPARING'::text) AND (published_at IS NULL) AND (aborted_at IS NULL) AND (abort_reason IS NULL)) OR (((status)::text = 'PUBLISHED'::text) AND (published_at IS NOT NULL) AND (aborted_at IS NULL) AND (abort_reason IS NULL)) OR (((status)::text = 'ABORTED'::text) AND (published_at IS NULL) AND (aborted_at IS NOT NULL) AND (btrim(abort_reason) <> ''::text)))),
    CONSTRAINT chk_projection_batch_generation CHECK (((expected_previous_generation >= 0) AND (generation = (expected_previous_generation + 1)))),
    CONSTRAINT chk_projection_batch_status CHECK (((status)::text = ANY ((ARRAY['PREPARING'::character varying, 'PUBLISHED'::character varying, 'ABORTED'::character varying])::text[])))
);


--
-- Name: projection_content_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projection_content_records (
    batch_id uuid NOT NULL,
    record_id character varying(255) NOT NULL,
    organization_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    chunk_id uuid,
    acl_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    content_kind character varying(64) NOT NULL,
    content text NOT NULL,
    token_count integer NOT NULL,
    metadata text NOT NULL,
    CONSTRAINT chk_projection_content_acl_generation CHECK ((acl_generation >= 0)),
    CONSTRAINT chk_projection_content_token_count CHECK ((token_count >= 0))
);


--
-- Name: projection_graph_entities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projection_graph_entities (
    batch_id uuid NOT NULL,
    entity_id uuid NOT NULL,
    normalized_name text NOT NULL
);


--
-- Name: projection_graph_entity_contributions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projection_graph_entity_contributions (
    batch_id uuid NOT NULL,
    contribution_id uuid NOT NULL,
    entity_id uuid NOT NULL,
    entity_type character varying(255) NOT NULL,
    description text NOT NULL,
    organization_id uuid NOT NULL,
    knowledge_asset_id uuid CONSTRAINT projection_graph_entity_contributio_knowledge_asset_id_not_null NOT NULL,
    source_revision_id uuid CONSTRAINT projection_graph_entity_contributio_source_revision_id_not_null NOT NULL,
    chunk_id uuid,
    acl_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    projection_generation bigint CONSTRAINT projection_graph_entity_contribu_projection_generation_not_null NOT NULL,
    extractor_provider character varying(255) CONSTRAINT projection_graph_entity_contributio_extractor_provider_not_null NOT NULL,
    extractor_model character varying(255) NOT NULL,
    prompt_version character varying(255) NOT NULL,
    extraction_profile_fingerprint character varying(64) CONSTRAINT projection_graph_entity_con_extraction_profile_fingerp_not_null NOT NULL,
    confidence double precision NOT NULL,
    extracted_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_projection_graph_entity_acl CHECK ((acl_generation >= 0)),
    CONSTRAINT chk_projection_graph_entity_confidence CHECK (((confidence >= (0.0)::double precision) AND (confidence <= (1.0)::double precision))),
    CONSTRAINT chk_projection_graph_entity_generation CHECK ((projection_generation >= 0))
);


--
-- Name: projection_graph_relation_contributions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projection_graph_relation_contributions (
    batch_id uuid NOT NULL,
    contribution_id uuid CONSTRAINT projection_graph_relation_contribution_contribution_id_not_null NOT NULL,
    relation_id uuid NOT NULL,
    relation_type character varying(255) NOT NULL,
    keywords text NOT NULL,
    description text NOT NULL,
    weight double precision NOT NULL,
    organization_id uuid CONSTRAINT projection_graph_relation_contribution_organization_id_not_null NOT NULL,
    knowledge_asset_id uuid CONSTRAINT projection_graph_relation_contribut_knowledge_asset_id_not_null NOT NULL,
    source_revision_id uuid CONSTRAINT projection_graph_relation_contribut_source_revision_id_not_null NOT NULL,
    chunk_id uuid,
    acl_snapshot_id uuid CONSTRAINT projection_graph_relation_contribution_acl_snapshot_id_not_null NOT NULL,
    acl_generation bigint NOT NULL,
    projection_generation bigint CONSTRAINT projection_graph_relation_contri_projection_generation_not_null NOT NULL,
    extractor_provider character varying(255) CONSTRAINT projection_graph_relation_contribut_extractor_provider_not_null NOT NULL,
    extractor_model character varying(255) CONSTRAINT projection_graph_relation_contribution_extractor_model_not_null NOT NULL,
    prompt_version character varying(255) NOT NULL,
    extraction_profile_fingerprint character varying(64) CONSTRAINT projection_graph_relation_c_extraction_profile_fingerp_not_null NOT NULL,
    confidence double precision NOT NULL,
    extracted_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_projection_graph_relation_acl CHECK ((acl_generation >= 0)),
    CONSTRAINT chk_projection_graph_relation_confidence CHECK (((confidence >= (0.0)::double precision) AND (confidence <= (1.0)::double precision))),
    CONSTRAINT chk_projection_graph_relation_generation CHECK ((projection_generation >= 0)),
    CONSTRAINT chk_projection_graph_relation_weight CHECK ((weight > (0.0)::double precision))
);


--
-- Name: projection_graph_relations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projection_graph_relations (
    batch_id uuid NOT NULL,
    relation_id uuid NOT NULL,
    source_entity_id uuid NOT NULL,
    target_entity_id uuid NOT NULL,
    orientation character varying(16) NOT NULL,
    CONSTRAINT chk_projection_graph_endpoints CHECK ((source_entity_id <> target_entity_id)),
    CONSTRAINT chk_projection_graph_orientation CHECK (((orientation)::text = ANY ((ARRAY['DIRECTED'::character varying, 'UNDIRECTED'::character varying])::text[])))
);


--
-- Name: projection_lexical_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projection_lexical_documents (
    batch_id uuid NOT NULL,
    document_id character varying(255) NOT NULL,
    organization_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    chunk_id uuid,
    acl_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    content text NOT NULL,
    fields text NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple'::regconfig, ((content || ' '::text) || fields))) STORED,
    CONSTRAINT chk_projection_lexical_acl_generation CHECK ((acl_generation >= 0))
);


--
-- Name: projection_namespace_heads; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projection_namespace_heads (
    organization_id uuid NOT NULL,
    workspace character varying(255) NOT NULL,
    collection_name character varying(255) NOT NULL,
    batch_id uuid NOT NULL,
    generation bigint NOT NULL,
    CONSTRAINT chk_projection_head_generation CHECK ((generation > 0))
);


--
-- Name: projection_publications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projection_publications (
    batch_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    workspace character varying(255) NOT NULL,
    collection_name character varying(255) NOT NULL,
    generation bigint NOT NULL,
    manifest_fingerprint character varying(255) NOT NULL,
    projections text NOT NULL,
    published_at timestamp with time zone NOT NULL
);


--
-- Name: projection_stage_initializations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projection_stage_initializations (
    batch_id uuid NOT NULL,
    projection_kind character varying(32) NOT NULL,
    initialized_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_projection_initialization_kind CHECK (((projection_kind)::text = ANY ((ARRAY['CONTENT'::character varying, 'LEXICAL'::character varying, 'VECTOR'::character varying, 'GRAPH'::character varying])::text[])))
);


--
-- Name: projection_vector_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projection_vector_records (
    batch_id uuid NOT NULL,
    record_id character varying(255) NOT NULL,
    subject_id character varying(255) NOT NULL,
    organization_id uuid NOT NULL,
    knowledge_asset_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    chunk_id uuid,
    acl_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    vector_kind character varying(32) NOT NULL,
    embedding_profile_id uuid NOT NULL,
    model character varying(255) NOT NULL,
    dimensions integer NOT NULL,
    embedding public.vector NOT NULL,
    metadata text NOT NULL,
    CONSTRAINT chk_projection_vector_acl_generation CHECK ((acl_generation >= 0)),
    CONSTRAINT chk_projection_vector_dimensions CHECK ((dimensions > 0)),
    CONSTRAINT chk_projection_vector_kind CHECK (((vector_kind)::text = ANY ((ARRAY['CHUNK'::character varying, 'ENTITY'::character varying, 'RELATION'::character varying])::text[])))
);


--
-- Name: raw_source_objects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.raw_source_objects (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    department_id uuid,
    source_system character varying(64) NOT NULL,
    source_connection_key character varying(128) NOT NULL,
    external_object_id character varying(512) NOT NULL,
    source_version character varying(255) NOT NULL,
    object_type character varying(64) NOT NULL,
    title character varying(255) NOT NULL,
    raw_content text NOT NULL,
    source_uri character varying(2048),
    payload_sha256 character varying(64) NOT NULL,
    source_modified_at timestamp with time zone,
    classification character varying(32),
    declared_access character varying(32),
    status character varying(32) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_raw_source_classification CHECK (((classification IS NULL) OR ((classification)::text = ANY ((ARRAY['PUBLIC'::character varying, 'INTERNAL'::character varying, 'CONFIDENTIAL'::character varying, 'RESTRICTED'::character varying])::text[])))),
    CONSTRAINT chk_raw_source_declared_access CHECK (((declared_access IS NULL) OR ((declared_access)::text = ANY ((ARRAY['ALL'::character varying, 'ALL_EMPLOYEES'::character varying, 'OWN_DEPARTMENT'::character varying, 'EXECUTIVE_ONLY'::character varying])::text[])))),
    CONSTRAINT chk_raw_source_nonblank CHECK (((btrim((source_system)::text) <> ''::text) AND (btrim((source_connection_key)::text) <> ''::text) AND (btrim((external_object_id)::text) <> ''::text) AND (btrim((source_version)::text) <> ''::text) AND (btrim((object_type)::text) <> ''::text) AND (btrim((title)::text) <> ''::text) AND (btrim(raw_content) <> ''::text))),
    CONSTRAINT chk_raw_source_payload_sha CHECK (((payload_sha256)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT chk_raw_source_status CHECK (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'NORMALIZED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: source_acl_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_acl_entries (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_acl_snapshot_id uuid NOT NULL,
    principal_type character varying(32) NOT NULL,
    principal_key character varying(512) NOT NULL,
    gate character varying(16) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_source_acl_entry_gate CHECK (((gate)::text = ANY ((ARRAY['ALLOW'::character varying, 'DENY'::character varying])::text[]))),
    CONSTRAINT chk_source_acl_principal_key CHECK ((btrim((principal_key)::text) <> ''::text)),
    CONSTRAINT chk_source_acl_principal_type CHECK (((principal_type)::text = ANY ((ARRAY['ORGMEMORY_USER'::character varying, 'ORGMEMORY_DEPARTMENT'::character varying, 'ORGMEMORY_ORGANIZATION'::character varying, 'SOURCE_USER'::character varying, 'SOURCE_GROUP'::character varying])::text[])))
);


--
-- Name: source_acl_group_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_acl_group_members (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_acl_snapshot_id uuid NOT NULL,
    group_principal_id uuid NOT NULL,
    group_principal_kind character varying(16) DEFAULT 'SOURCE_GROUP'::character varying NOT NULL,
    member_principal_id uuid NOT NULL,
    member_principal_kind character varying(16) DEFAULT 'SOURCE_USER'::character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_source_acl_group_member_group_kind CHECK (((group_principal_kind)::text = 'SOURCE_GROUP'::text)),
    CONSTRAINT chk_source_acl_group_member_member_kind CHECK (((member_principal_kind)::text = 'SOURCE_USER'::text))
);


--
-- Name: source_acl_heads; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_acl_heads (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_system character varying(64) NOT NULL,
    source_connection_key character varying(128) NOT NULL,
    external_object_id character varying(512) NOT NULL,
    current_raw_source_object_id uuid NOT NULL,
    current_snapshot_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_source_acl_head_generation CHECK ((acl_generation > 0)),
    CONSTRAINT chk_source_acl_head_nonblank CHECK (((btrim((source_system)::text) <> ''::text) AND (btrim((source_connection_key)::text) <> ''::text) AND (btrim((external_object_id)::text) <> ''::text)))
);


--
-- Name: source_acl_snapshot_seals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_acl_snapshot_seals (
    source_acl_snapshot_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    entry_count integer NOT NULL,
    entries_sha256 character varying(64) NOT NULL,
    sealed_at timestamp with time zone NOT NULL,
    CONSTRAINT chk_source_acl_seal_entry_count CHECK ((entry_count >= 0)),
    CONSTRAINT chk_source_acl_seal_sha CHECK (((entries_sha256)::text ~ '^[0-9a-f]{64}$'::text))
);


--
-- Name: source_acl_snapshots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_acl_snapshots (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    raw_source_object_id uuid NOT NULL,
    acl_generation bigint NOT NULL,
    capture_status character varying(32) NOT NULL,
    default_gate character varying(16) NOT NULL,
    acl_sha256 character varying(64),
    captured_at timestamp with time zone NOT NULL,
    valid_until timestamp with time zone,
    CONSTRAINT chk_source_acl_capture_status CHECK (((capture_status)::text = ANY ((ARRAY['COMPLETE'::character varying, 'UNKNOWN'::character varying, 'UNSUPPORTED'::character varying])::text[]))),
    CONSTRAINT chk_source_acl_completeness CHECK (((((capture_status)::text = 'COMPLETE'::text) AND (acl_sha256 IS NOT NULL) AND (valid_until IS NOT NULL) AND (valid_until > captured_at) AND (valid_until <= (captured_at + '24:00:00'::interval))) OR (((capture_status)::text = ANY ((ARRAY['UNKNOWN'::character varying, 'UNSUPPORTED'::character varying])::text[])) AND ((default_gate)::text = 'UNKNOWN'::text) AND (valid_until IS NULL)))),
    CONSTRAINT chk_source_acl_default_gate CHECK (((default_gate)::text = ANY ((ARRAY['ALLOW'::character varying, 'DENY'::character varying, 'UNKNOWN'::character varying])::text[]))),
    CONSTRAINT chk_source_acl_generation CHECK ((acl_generation > 0)),
    CONSTRAINT chk_source_acl_sha CHECK (((acl_sha256 IS NULL) OR ((acl_sha256)::text ~ '^[0-9a-f]{64}$'::text)))
);


--
-- Name: source_connection_credentials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_connection_credentials (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_connection_id uuid NOT NULL,
    cipher_text text NOT NULL,
    key_version integer NOT NULL,
    set_by_user_id uuid NOT NULL,
    set_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_source_connection_credential_cipher CHECK ((btrim(cipher_text) <> ''::text)),
    CONSTRAINT chk_source_connection_credential_key_version CHECK ((key_version >= 1))
);


--
-- Name: TABLE source_connection_credentials; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.source_connection_credentials IS 'Encrypted source credentials. cipher_text is AES-256-GCM ciphertext; key_version records which key produced it so a rotation can select what it has yet to re-encrypt rather than trial-decrypting every row.';


--
-- Name: source_connections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_connections (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_system character varying(64) NOT NULL,
    source_connection_key character varying(128) NOT NULL,
    identity_trust character varying(32) DEFAULT 'UNTRUSTED'::character varying NOT NULL,
    trust_decided_by_user_id uuid,
    trust_decided_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    crawl_enabled boolean DEFAULT false NOT NULL,
    knowledge_space_id uuid,
    actor_user_id uuid,
    content_crawl_interval_seconds integer DEFAULT 3600 NOT NULL,
    crawl_configured_by_user_id uuid,
    crawl_configured_at timestamp with time zone,
    source_config jsonb DEFAULT '{}'::jsonb NOT NULL,
    content_crawl_requested_at timestamp with time zone,
    CONSTRAINT chk_source_connection_config_object CHECK ((jsonb_typeof(source_config) = 'object'::text)),
    CONSTRAINT chk_source_connection_crawl_interval CHECK ((content_crawl_interval_seconds > 0)),
    CONSTRAINT chk_source_connection_crawl_targets CHECK (((crawl_enabled = false) OR ((knowledge_space_id IS NOT NULL) AND (actor_user_id IS NOT NULL)))),
    CONSTRAINT chk_source_connection_identity_trust CHECK (((identity_trust)::text = ANY ((ARRAY['UNTRUSTED'::character varying, 'SSO_VERIFIED'::character varying])::text[]))),
    CONSTRAINT chk_source_connection_nonblank CHECK (((btrim((source_system)::text) <> ''::text) AND (btrim((source_connection_key)::text) <> ''::text))),
    CONSTRAINT chk_source_connection_trust_attribution CHECK (((((identity_trust)::text = 'UNTRUSTED'::text) AND (trust_decided_by_user_id IS NULL) AND (trust_decided_at IS NULL)) OR (((identity_trust)::text <> 'UNTRUSTED'::text) AND (trust_decided_by_user_id IS NOT NULL) AND (trust_decided_at IS NOT NULL))))
);


--
-- Name: COLUMN source_connections.source_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.source_connections.source_config IS 'Settings only this source system understands, as a JSON object. The ledger stores and returns it without reading inside: a column shape covering every source does not exist, and pretending otherwise is what made adding a source a migration.';


--
-- Name: source_ingestion_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_ingestion_jobs (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_revision_id uuid NOT NULL,
    job_type character varying(64) NOT NULL,
    status character varying(32) NOT NULL,
    available_at timestamp with time zone NOT NULL,
    lease_owner character varying(128),
    lease_until timestamp with time zone,
    attempt_count integer NOT NULL,
    max_attempts integer NOT NULL,
    last_error_code character varying(64),
    last_error_message character varying(512),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_source_ingestion_job_attempts CHECK (((attempt_count >= 0) AND (max_attempts > 0) AND (attempt_count <= max_attempts))),
    CONSTRAINT chk_source_ingestion_job_lease CHECK (((((status)::text = 'PROCESSING'::text) AND (lease_owner IS NOT NULL) AND (lease_until IS NOT NULL)) OR ((status)::text <> 'PROCESSING'::text))),
    CONSTRAINT chk_source_ingestion_job_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT chk_source_ingestion_job_type CHECK (((job_type)::text = 'PROCESS_SOURCE_REVISION'::text))
);


--
-- Name: source_objects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_objects (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    department_id uuid,
    created_by_user_id uuid NOT NULL,
    acl_authority character varying(32) CONSTRAINT source_objects_source_type_not_null NOT NULL,
    source_connection_key character varying(128) NOT NULL,
    external_object_id character varying(512) NOT NULL,
    title character varying(255) NOT NULL,
    classification character varying(32) NOT NULL,
    declared_access character varying(32) NOT NULL,
    current_revision_id uuid,
    status character varying(32) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    knowledge_space_id uuid NOT NULL,
    latest_revision_id uuid,
    source_system character varying(64) NOT NULL,
    CONSTRAINT chk_source_object_acl_authority CHECK (((acl_authority)::text = ANY ((ARRAY['ORGMEMORY'::character varying, 'SOURCE'::character varying])::text[]))),
    CONSTRAINT chk_source_object_classification_access CHECK (((((classification)::text = 'PUBLIC'::text) AND ((declared_access)::text = 'ALL'::text)) OR (((classification)::text = 'INTERNAL'::text) AND ((declared_access)::text = 'ALL_EMPLOYEES'::text)) OR (((classification)::text = 'CONFIDENTIAL'::text) AND ((declared_access)::text = 'OWN_DEPARTMENT'::text)) OR (((classification)::text = 'RESTRICTED'::text) AND ((declared_access)::text = 'EXECUTIVE_ONLY'::text)))),
    CONSTRAINT chk_source_object_confidential_department CHECK ((((classification)::text <> 'CONFIDENTIAL'::text) OR (department_id IS NOT NULL))),
    CONSTRAINT chk_source_object_nonblank CHECK (((btrim((source_connection_key)::text) <> ''::text) AND (btrim((external_object_id)::text) <> ''::text) AND (btrim((title)::text) <> ''::text))),
    CONSTRAINT chk_source_object_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'ARCHIVED'::character varying])::text[]))),
    CONSTRAINT chk_source_object_system CHECK ((btrim((source_system)::text) <> ''::text))
);


--
-- Name: COLUMN source_objects.acl_authority; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.source_objects.acl_authority IS 'Who decides who may read this object. ORGMEMORY keeps the ingestion ACL intersected with the current one; SOURCE enforces only the latest sealed generation because the source still owns the decision. Recorded at ingestion and never updated: it is what was true when the evidence entered, not a policy an administrator can change afterwards.';


--
-- Name: COLUMN source_objects.source_system; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.source_objects.source_system IS 'Which system the object came from, such as slack or upload. Governed by the connector registry rather than a check constraint, so a new connector needs no migration.';


--
-- Name: source_principal_mappings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_principal_mappings (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_principal_id uuid NOT NULL,
    source_principal_kind character varying(16) DEFAULT 'SOURCE_USER'::character varying NOT NULL,
    app_user_id uuid NOT NULL,
    method character varying(32) NOT NULL,
    evidence character varying(512) NOT NULL,
    status character varying(16) NOT NULL,
    verified_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_source_principal_mapping_evidence CHECK ((btrim((evidence)::text) <> ''::text)),
    CONSTRAINT chk_source_principal_mapping_kind CHECK (((source_principal_kind)::text = 'SOURCE_USER'::text)),
    CONSTRAINT chk_source_principal_mapping_method CHECK (((method)::text = ANY ((ARRAY['IDP_JOIN'::character varying, 'SSO_EMAIL_JOIN'::character varying, 'SELF_CLAIM'::character varying, 'ADMIN_CONFIRMED'::character varying])::text[]))),
    CONSTRAINT chk_source_principal_mapping_status CHECK (((((status)::text = 'ACTIVE'::text) AND (revoked_at IS NULL)) OR (((status)::text = 'REVOKED'::text) AND (revoked_at IS NOT NULL))))
);


--
-- Name: source_principals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_principals (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_system character varying(64) NOT NULL,
    source_connection_key character varying(128) NOT NULL,
    external_key character varying(512) NOT NULL,
    kind character varying(16) NOT NULL,
    observed_email character varying(320),
    observed_display_name character varying(256),
    sso_verified boolean DEFAULT false NOT NULL,
    last_seen_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_source_principal_email CHECK (((observed_email IS NULL) OR (btrim((observed_email)::text) <> ''::text))),
    CONSTRAINT chk_source_principal_kind CHECK (((kind)::text = ANY ((ARRAY['SOURCE_USER'::character varying, 'SOURCE_GROUP'::character varying])::text[]))),
    CONSTRAINT chk_source_principal_nonblank CHECK (((btrim((source_system)::text) <> ''::text) AND (btrim((source_connection_key)::text) <> ''::text) AND (btrim((external_key)::text) <> ''::text)))
);


--
-- Name: source_revisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_revisions (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_object_id uuid NOT NULL,
    evidence_blob_id uuid NOT NULL,
    revision_number bigint NOT NULL,
    file_name character varying(255) NOT NULL,
    media_type character varying(255) NOT NULL,
    content_length bigint NOT NULL,
    content_sha256 character varying(64) NOT NULL,
    classification character varying(32) NOT NULL,
    declared_access character varying(32) NOT NULL,
    department_id uuid,
    created_by_user_id uuid NOT NULL,
    status character varying(32) NOT NULL,
    failure_code character varying(64),
    failure_message character varying(512),
    pipeline_version character varying(64),
    parser_version character varying(64),
    chunker_version character varying(64),
    embedding_profile_id uuid,
    embedding_dimensions integer,
    raw_source_object_id uuid,
    normalized_record_id uuid,
    knowledge_asset_id uuid,
    processed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    knowledge_space_id uuid NOT NULL,
    knowledge_asset_version_id uuid,
    processing_profile text,
    processing_profile_sha256 character varying(64),
    CONSTRAINT chk_source_revision_embedding_dimensions CHECK (((embedding_dimensions IS NULL) OR (embedding_dimensions > 0))),
    CONSTRAINT chk_source_revision_length CHECK ((content_length > 0)),
    CONSTRAINT chk_source_revision_nonblank CHECK (((btrim((file_name)::text) <> ''::text) AND (btrim((media_type)::text) <> ''::text))),
    CONSTRAINT chk_source_revision_number CHECK ((revision_number > 0)),
    CONSTRAINT chk_source_revision_processing_profile CHECK ((((processing_profile IS NULL) AND (processing_profile_sha256 IS NULL)) OR ((btrim(processing_profile) <> ''::text) AND ((processing_profile_sha256)::text ~ '^[0-9a-f]{64}$'::text)))),
    CONSTRAINT chk_source_revision_ready CHECK ((((status)::text <> 'READY'::text) OR ((pipeline_version IS NOT NULL) AND (parser_version IS NOT NULL) AND (chunker_version IS NOT NULL) AND (embedding_profile_id IS NOT NULL) AND (embedding_dimensions IS NOT NULL) AND (knowledge_asset_id IS NOT NULL) AND (processed_at IS NOT NULL) AND (failure_code IS NULL)))),
    CONSTRAINT chk_source_revision_sha CHECK (((content_sha256)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT chk_source_revision_status CHECK (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'VALIDATING'::character varying, 'PARSING'::character varying, 'CHUNKING'::character varying, 'EMBEDDING'::character varying, 'PUBLISHING'::character varying, 'READY'::character varying, 'QUARANTINED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: COLUMN source_revisions.processing_profile; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.source_revisions.processing_profile IS 'Canonical resolved parser, chunker, tokenizer, model, and option snapshot.';


--
-- Name: spring_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spring_session (
    primary_id character(36) NOT NULL,
    session_id character(36) NOT NULL,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name character varying(100)
);


--
-- Name: spring_session_attributes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spring_session_attributes (
    session_primary_id character(36) NOT NULL,
    attribute_name character varying(200) NOT NULL,
    attribute_bytes bytea NOT NULL
);


--
-- Name: user_invitations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_invitations (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    email character varying(255) NOT NULL,
    department_id uuid,
    role character varying(32) NOT NULL,
    invited_by_user_id uuid NOT NULL,
    revoked_at timestamp with time zone,
    accepted_at timestamp with time zone,
    accepted_app_user_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT chk_user_invitation_acceptance CHECK ((((accepted_at IS NULL) AND (accepted_app_user_id IS NULL)) OR ((accepted_at IS NOT NULL) AND (accepted_app_user_id IS NOT NULL)))),
    CONSTRAINT chk_user_invitation_email CHECK ((btrim((email)::text) <> ''::text)),
    CONSTRAINT chk_user_invitation_role CHECK (((role)::text = ANY ((ARRAY['EMPLOYEE'::character varying, 'TEAM_LEAD'::character varying, 'MANAGER'::character varying, 'DIRECTOR'::character varying, 'EXECUTIVE'::character varying, 'ADMIN'::character varying])::text[]))),
    CONSTRAINT chk_user_invitation_terminal CHECK (((revoked_at IS NULL) OR (accepted_at IS NULL)))
);


--
-- Name: app_users app_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_users
    ADD CONSTRAINT app_users_pkey PRIMARY KEY (id);


--
-- Name: connector_crawl_attempts connector_crawl_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.connector_crawl_attempts
    ADD CONSTRAINT connector_crawl_attempts_pkey PRIMARY KEY (id);


--
-- Name: connector_crawl_checkpoints connector_crawl_checkpoints_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.connector_crawl_checkpoints
    ADD CONSTRAINT connector_crawl_checkpoints_pkey PRIMARY KEY (id);


--
-- Name: departments departments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT departments_pkey PRIMARY KEY (id);


--
-- Name: embedding_profiles embedding_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.embedding_profiles
    ADD CONSTRAINT embedding_profiles_pkey PRIMARY KEY (id);


--
-- Name: evidence_blobs evidence_blobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evidence_blobs
    ADD CONSTRAINT evidence_blobs_pkey PRIMARY KEY (id);


--
-- Name: external_identities external_identities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.external_identities
    ADD CONSTRAINT external_identities_pkey PRIMARY KEY (id);


--
-- Name: graph_curation_records graph_curation_records_organization_id_workspace_collection_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_curation_records
    ADD CONSTRAINT graph_curation_records_organization_id_workspace_collection_key UNIQUE (organization_id, workspace, collection_name, idempotency_key);


--
-- Name: graph_curation_records graph_curation_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_curation_records
    ADD CONSTRAINT graph_curation_records_pkey PRIMARY KEY (id);


--
-- Name: graph_processing_profiles graph_processing_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_processing_profiles
    ADD CONSTRAINT graph_processing_profiles_pkey PRIMARY KEY (id);


--
-- Name: graph_index_jobs graph_index_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_index_jobs
    ADD CONSTRAINT graph_index_jobs_pkey PRIMARY KEY (id);


--
-- Name: graph_model_invocation_cache graph_model_invocation_cache_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_model_invocation_cache
    ADD CONSTRAINT graph_model_invocation_cache_pkey PRIMARY KEY (organization_id, workspace, collection_name, operation, input_hash, model_route_fingerprint, profile_fingerprint);


--
-- Name: graph_retrieval_cache_evidence graph_retrieval_cache_evidence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_retrieval_cache_evidence
    ADD CONSTRAINT graph_retrieval_cache_evidence_pkey PRIMARY KEY (cache_entry_id, ordinal);


--
-- Name: graph_retrieval_result_cache graph_retrieval_result_cache_organization_id_workspace_coll_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_retrieval_result_cache
    ADD CONSTRAINT graph_retrieval_result_cache_organization_id_workspace_coll_key UNIQUE (organization_id, workspace, collection_name, publication_batch_id, publication_generation, publication_manifest_fingerprint, publication_kinds, authorization_fingerprint, query_hash, strategy, model_route_fingerprint);


--
-- Name: graph_retrieval_result_cache graph_retrieval_result_cache_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_retrieval_result_cache
    ADD CONSTRAINT graph_retrieval_result_cache_pkey PRIMARY KEY (id);


--
-- Name: knowledge_asset_evidence_links knowledge_asset_evidence_links_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_evidence_links
    ADD CONSTRAINT knowledge_asset_evidence_links_pkey PRIMARY KEY (id);


--
-- Name: knowledge_asset_publication_outbox knowledge_asset_publication_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_publication_outbox
    ADD CONSTRAINT knowledge_asset_publication_outbox_pkey PRIMARY KEY (id);


--
-- Name: knowledge_asset_versions knowledge_assets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_versions
    ADD CONSTRAINT knowledge_assets_pkey PRIMARY KEY (id);


--
-- Name: knowledge_assets knowledge_assets_pkey1; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_assets
    ADD CONSTRAINT knowledge_assets_pkey1 PRIMARY KEY (id);


--
-- Name: knowledge_chunks knowledge_chunks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunks
    ADD CONSTRAINT knowledge_chunks_pkey PRIMARY KEY (id);


--
-- Name: knowledge_spaces knowledge_spaces_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_spaces
    ADD CONSTRAINT knowledge_spaces_pkey PRIMARY KEY (id);


--
-- Name: normalized_records normalized_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.normalized_records
    ADD CONSTRAINT normalized_records_pkey PRIMARY KEY (id);


--
-- Name: organizations organizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT organizations_pkey PRIMARY KEY (id);


--
-- Name: permission_audit_events permission_audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_audit_events
    ADD CONSTRAINT permission_audit_events_pkey PRIMARY KEY (id);


--
-- Name: projection_batch_receipts projection_batch_receipts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_batch_receipts
    ADD CONSTRAINT projection_batch_receipts_pkey PRIMARY KEY (batch_id, projection_kind);


--
-- Name: projection_batches projection_batches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_batches
    ADD CONSTRAINT projection_batches_pkey PRIMARY KEY (batch_id);


--
-- Name: projection_content_records projection_content_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_content_records
    ADD CONSTRAINT projection_content_records_pkey PRIMARY KEY (batch_id, record_id);


--
-- Name: projection_graph_entities projection_graph_entities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_graph_entities
    ADD CONSTRAINT projection_graph_entities_pkey PRIMARY KEY (batch_id, entity_id);


--
-- Name: projection_graph_entity_contributions projection_graph_entity_contributions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_graph_entity_contributions
    ADD CONSTRAINT projection_graph_entity_contributions_pkey PRIMARY KEY (batch_id, contribution_id);


--
-- Name: projection_graph_relation_contributions projection_graph_relation_contributions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_graph_relation_contributions
    ADD CONSTRAINT projection_graph_relation_contributions_pkey PRIMARY KEY (batch_id, contribution_id);


--
-- Name: projection_graph_relations projection_graph_relations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_graph_relations
    ADD CONSTRAINT projection_graph_relations_pkey PRIMARY KEY (batch_id, relation_id);


--
-- Name: projection_lexical_documents projection_lexical_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_lexical_documents
    ADD CONSTRAINT projection_lexical_documents_pkey PRIMARY KEY (batch_id, document_id);


--
-- Name: projection_namespace_heads projection_namespace_heads_batch_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_namespace_heads
    ADD CONSTRAINT projection_namespace_heads_batch_id_key UNIQUE (batch_id);


--
-- Name: projection_namespace_heads projection_namespace_heads_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_namespace_heads
    ADD CONSTRAINT projection_namespace_heads_pkey PRIMARY KEY (organization_id, workspace, collection_name);


--
-- Name: projection_publications projection_publications_organization_id_workspace_collectio_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_publications
    ADD CONSTRAINT projection_publications_organization_id_workspace_collectio_key UNIQUE (organization_id, workspace, collection_name, generation);


--
-- Name: projection_publications projection_publications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_publications
    ADD CONSTRAINT projection_publications_pkey PRIMARY KEY (batch_id);


--
-- Name: projection_stage_initializations projection_stage_initializations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_stage_initializations
    ADD CONSTRAINT projection_stage_initializations_pkey PRIMARY KEY (batch_id, projection_kind);


--
-- Name: projection_vector_records projection_vector_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_vector_records
    ADD CONSTRAINT projection_vector_records_pkey PRIMARY KEY (batch_id, record_id);


--
-- Name: raw_source_objects raw_source_objects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.raw_source_objects
    ADD CONSTRAINT raw_source_objects_pkey PRIMARY KEY (id);


--
-- Name: source_acl_entries source_acl_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_entries
    ADD CONSTRAINT source_acl_entries_pkey PRIMARY KEY (id);


--
-- Name: source_acl_group_members source_acl_group_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_group_members
    ADD CONSTRAINT source_acl_group_members_pkey PRIMARY KEY (id);


--
-- Name: source_acl_heads source_acl_heads_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_heads
    ADD CONSTRAINT source_acl_heads_pkey PRIMARY KEY (id);


--
-- Name: source_acl_snapshot_seals source_acl_snapshot_seals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_snapshot_seals
    ADD CONSTRAINT source_acl_snapshot_seals_pkey PRIMARY KEY (source_acl_snapshot_id);


--
-- Name: source_acl_snapshots source_acl_snapshots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_snapshots
    ADD CONSTRAINT source_acl_snapshots_pkey PRIMARY KEY (id);


--
-- Name: source_connection_credentials source_connection_credentials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_connection_credentials
    ADD CONSTRAINT source_connection_credentials_pkey PRIMARY KEY (id);


--
-- Name: source_connection_credentials source_connection_credentials_source_connection_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_connection_credentials
    ADD CONSTRAINT source_connection_credentials_source_connection_id_key UNIQUE (source_connection_id);


--
-- Name: source_connections source_connections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_connections
    ADD CONSTRAINT source_connections_pkey PRIMARY KEY (id);


--
-- Name: source_ingestion_jobs source_ingestion_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_ingestion_jobs
    ADD CONSTRAINT source_ingestion_jobs_pkey PRIMARY KEY (id);


--
-- Name: source_objects source_objects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_objects
    ADD CONSTRAINT source_objects_pkey PRIMARY KEY (id);


--
-- Name: source_principal_mappings source_principal_mappings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_principal_mappings
    ADD CONSTRAINT source_principal_mappings_pkey PRIMARY KEY (id);


--
-- Name: source_principals source_principals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_principals
    ADD CONSTRAINT source_principals_pkey PRIMARY KEY (id);


--
-- Name: source_revisions source_revisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT source_revisions_pkey PRIMARY KEY (id);


--
-- Name: spring_session_attributes spring_session_attributes_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name);


--
-- Name: spring_session spring_session_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session
    ADD CONSTRAINT spring_session_pk PRIMARY KEY (primary_id);


--
-- Name: app_users uq_app_user_id_organization; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_users
    ADD CONSTRAINT uq_app_user_id_organization UNIQUE (id, organization_id);


--
-- Name: connector_crawl_checkpoints uq_connector_checkpoint_connection; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.connector_crawl_checkpoints
    ADD CONSTRAINT uq_connector_checkpoint_connection UNIQUE (organization_id, source_system, source_connection_key);


--
-- Name: departments uq_departments_id_organization; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT uq_departments_id_organization UNIQUE (id, organization_id);


--
-- Name: embedding_profiles uq_embedding_profile_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.embedding_profiles
    ADD CONSTRAINT uq_embedding_profile_key UNIQUE (organization_id, profile_key);


--
-- Name: embedding_profiles uq_embedding_profile_projection; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.embedding_profiles
    ADD CONSTRAINT uq_embedding_profile_projection UNIQUE (id, organization_id, dimensions);


--
-- Name: graph_processing_profiles uq_graph_processing_profile_sha; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_processing_profiles
    ADD CONSTRAINT uq_graph_processing_profile_sha UNIQUE (canonical_sha256);


--
-- Name: evidence_blobs uq_evidence_blob_id_organization; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evidence_blobs
    ADD CONSTRAINT uq_evidence_blob_id_organization UNIQUE (id, organization_id);


--
-- Name: evidence_blobs uq_evidence_blob_object_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evidence_blobs
    ADD CONSTRAINT uq_evidence_blob_object_key UNIQUE (object_key);


--
-- Name: external_identities uq_external_identity_issuer_subject; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.external_identities
    ADD CONSTRAINT uq_external_identity_issuer_subject UNIQUE (issuer, subject);


--
-- Name: external_identities uq_external_identity_user_issuer; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.external_identities
    ADD CONSTRAINT uq_external_identity_user_issuer UNIQUE (app_user_id, issuer);


--
-- Name: graph_index_jobs uq_graph_index_job_version_profile; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_index_jobs
    ADD CONSTRAINT uq_graph_index_job_version_profile UNIQUE (knowledge_asset_version_id, graph_processing_profile_id);


--
-- Name: knowledge_asset_evidence_links uq_knowledge_asset_evidence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_evidence_links
    ADD CONSTRAINT uq_knowledge_asset_evidence UNIQUE (knowledge_asset_version_id, source_revision_id, source_acl_snapshot_id);


--
-- Name: knowledge_assets uq_knowledge_asset_id_organization; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_assets
    ADD CONSTRAINT uq_knowledge_asset_id_organization UNIQUE (id, organization_id);


--
-- Name: knowledge_asset_publication_outbox uq_knowledge_asset_publication_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_publication_outbox
    ADD CONSTRAINT uq_knowledge_asset_publication_version UNIQUE (knowledge_asset_version_id);


--
-- Name: knowledge_assets uq_knowledge_asset_source; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_assets
    ADD CONSTRAINT uq_knowledge_asset_source UNIQUE (organization_id, source_object_id);


--
-- Name: knowledge_asset_versions uq_knowledge_asset_version_chain; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_versions
    ADD CONSTRAINT uq_knowledge_asset_version_chain UNIQUE (id, organization_id, knowledge_asset_id);


--
-- Name: knowledge_asset_versions uq_knowledge_asset_version_id_organization; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_versions
    ADD CONSTRAINT uq_knowledge_asset_version_id_organization UNIQUE (id, organization_id);


--
-- Name: knowledge_asset_versions uq_knowledge_asset_version_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_versions
    ADD CONSTRAINT uq_knowledge_asset_version_number UNIQUE (knowledge_asset_id, version_number);


--
-- Name: knowledge_chunks uq_knowledge_chunk_graph_provenance; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunks
    ADD CONSTRAINT uq_knowledge_chunk_graph_provenance UNIQUE (id, organization_id, source_revision_id, knowledge_asset_id, projection_generation);


--
-- Name: knowledge_chunks uq_knowledge_chunk_revision_index; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunks
    ADD CONSTRAINT uq_knowledge_chunk_revision_index UNIQUE (source_revision_id, chunk_index);


--
-- Name: knowledge_chunks uq_knowledge_chunk_version_provenance; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunks
    ADD CONSTRAINT uq_knowledge_chunk_version_provenance UNIQUE (id, organization_id, source_revision_id, knowledge_asset_id, knowledge_asset_version_id, projection_generation);


--
-- Name: knowledge_asset_versions uq_knowledge_id_organization; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_versions
    ADD CONSTRAINT uq_knowledge_id_organization UNIQUE (id, organization_id);


--
-- Name: knowledge_asset_versions uq_knowledge_normalized; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_versions
    ADD CONSTRAINT uq_knowledge_normalized UNIQUE (normalized_record_id);


--
-- Name: knowledge_spaces uq_knowledge_space_id_organization; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_spaces
    ADD CONSTRAINT uq_knowledge_space_id_organization UNIQUE (id, organization_id);


--
-- Name: knowledge_spaces uq_knowledge_space_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_spaces
    ADD CONSTRAINT uq_knowledge_space_key UNIQUE (organization_id, space_key);


--
-- Name: normalized_records uq_normalized_chain; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.normalized_records
    ADD CONSTRAINT uq_normalized_chain UNIQUE (id, organization_id, raw_source_object_id, source_acl_snapshot_id);


--
-- Name: normalized_records uq_normalized_raw_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.normalized_records
    ADD CONSTRAINT uq_normalized_raw_version UNIQUE (raw_source_object_id, normalizer_version);


--
-- Name: raw_source_objects uq_raw_source_id_organization; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.raw_source_objects
    ADD CONSTRAINT uq_raw_source_id_organization UNIQUE (id, organization_id);


--
-- Name: raw_source_objects uq_raw_source_identity_chain; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.raw_source_objects
    ADD CONSTRAINT uq_raw_source_identity_chain UNIQUE (id, organization_id, source_system, source_connection_key, external_object_id);


--
-- Name: raw_source_objects uq_raw_source_revision; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.raw_source_objects
    ADD CONSTRAINT uq_raw_source_revision UNIQUE (organization_id, source_system, source_connection_key, external_object_id, source_version);


--
-- Name: source_acl_snapshots uq_source_acl_generation_chain; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_snapshots
    ADD CONSTRAINT uq_source_acl_generation_chain UNIQUE (id, organization_id, raw_source_object_id, acl_generation);


--
-- Name: source_acl_snapshots uq_source_acl_graph_generation; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_snapshots
    ADD CONSTRAINT uq_source_acl_graph_generation UNIQUE (id, organization_id, acl_generation);


--
-- Name: source_acl_group_members uq_source_acl_group_member; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_group_members
    ADD CONSTRAINT uq_source_acl_group_member UNIQUE (source_acl_snapshot_id, group_principal_id, member_principal_id);


--
-- Name: source_acl_heads uq_source_acl_head_identity; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_heads
    ADD CONSTRAINT uq_source_acl_head_identity UNIQUE (organization_id, source_system, source_connection_key, external_object_id);


--
-- Name: source_acl_snapshots uq_source_acl_id_organization; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_snapshots
    ADD CONSTRAINT uq_source_acl_id_organization UNIQUE (id, organization_id);


--
-- Name: source_acl_snapshots uq_source_acl_id_organization_raw; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_snapshots
    ADD CONSTRAINT uq_source_acl_id_organization_raw UNIQUE (id, organization_id, raw_source_object_id);


--
-- Name: source_acl_entries uq_source_acl_principal; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_entries
    ADD CONSTRAINT uq_source_acl_principal UNIQUE (source_acl_snapshot_id, principal_type, principal_key);


--
-- Name: source_acl_snapshots uq_source_acl_raw_generation; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_snapshots
    ADD CONSTRAINT uq_source_acl_raw_generation UNIQUE (raw_source_object_id, acl_generation);


--
-- Name: source_acl_snapshot_seals uq_source_acl_seal_snapshot_organization; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_snapshot_seals
    ADD CONSTRAINT uq_source_acl_seal_snapshot_organization UNIQUE (source_acl_snapshot_id, organization_id);


--
-- Name: source_connections uq_source_connection_identity; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_connections
    ADD CONSTRAINT uq_source_connection_identity UNIQUE (organization_id, source_system, source_connection_key);


--
-- Name: source_ingestion_jobs uq_source_ingestion_job; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_ingestion_jobs
    ADD CONSTRAINT uq_source_ingestion_job UNIQUE (source_revision_id, job_type);


--
-- Name: source_objects uq_source_object_id_organization; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_objects
    ADD CONSTRAINT uq_source_object_id_organization UNIQUE (id, organization_id);


--
-- Name: source_objects uq_source_object_identity; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_objects
    ADD CONSTRAINT uq_source_object_identity UNIQUE (organization_id, source_system, source_connection_key, external_object_id);


--
-- Name: source_principals uq_source_principal_id_organization; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_principals
    ADD CONSTRAINT uq_source_principal_id_organization UNIQUE (id, organization_id);


--
-- Name: source_principals uq_source_principal_id_organization_kind; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_principals
    ADD CONSTRAINT uq_source_principal_id_organization_kind UNIQUE (id, organization_id, kind);


--
-- Name: source_principals uq_source_principal_identity; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_principals
    ADD CONSTRAINT uq_source_principal_identity UNIQUE (organization_id, source_system, source_connection_key, external_key);


--
-- Name: source_revisions uq_source_revision_asset_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT uq_source_revision_asset_version UNIQUE (id, organization_id, knowledge_asset_id, knowledge_asset_version_id);


--
-- Name: source_revisions uq_source_revision_chain; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT uq_source_revision_chain UNIQUE (id, organization_id, source_object_id);


--
-- Name: source_revisions uq_source_revision_content; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT uq_source_revision_content UNIQUE (source_object_id, content_sha256);


--
-- Name: source_revisions uq_source_revision_graph_asset; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT uq_source_revision_graph_asset UNIQUE (id, organization_id, knowledge_asset_id);


--
-- Name: source_revisions uq_source_revision_id_organization; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT uq_source_revision_id_organization UNIQUE (id, organization_id);


--
-- Name: source_revisions uq_source_revision_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT uq_source_revision_number UNIQUE (source_object_id, revision_number);


--
-- Name: user_invitations user_invitations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_invitations
    ADD CONSTRAINT user_invitations_pkey PRIMARY KEY (id);


--
-- Name: idx_connector_crawl_attempt_recent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_connector_crawl_attempt_recent ON public.connector_crawl_attempts USING btree (organization_id, source_system, source_connection_key, attempted_at DESC);


--
-- Name: idx_external_identities_app_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_external_identities_app_user_id ON public.external_identities USING btree (app_user_id);


--
-- Name: idx_graph_curation_active_namespace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_graph_curation_active_namespace ON public.graph_curation_records USING btree (organization_id, workspace, collection_name, curated_at, id) WHERE active;


--
-- Name: idx_graph_index_job_claim; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_graph_index_job_claim ON public.graph_index_jobs USING btree (status, available_at, created_at) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying])::text[]));


--
-- Name: idx_graph_index_job_expired_lease; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_graph_index_job_expired_lease ON public.graph_index_jobs USING btree (lease_until) WHERE ((status)::text = 'PROCESSING'::text);


--
-- Name: idx_graph_model_cache_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_graph_model_cache_expiry ON public.graph_model_invocation_cache USING btree (expires_at);


--
-- Name: idx_graph_retrieval_cache_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_graph_retrieval_cache_expiry ON public.graph_retrieval_result_cache USING btree (expires_at);


--
-- Name: idx_knowledge_asset_current_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_asset_current_version ON public.knowledge_assets USING btree (organization_id, current_version_id) WHERE (current_version_id IS NOT NULL);


--
-- Name: idx_knowledge_asset_evidence_revision; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_asset_evidence_revision ON public.knowledge_asset_evidence_links USING btree (organization_id, source_revision_id);


--
-- Name: idx_knowledge_asset_publication_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_asset_publication_pending ON public.knowledge_asset_publication_outbox USING btree (organization_id, created_at) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_knowledge_asset_space; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_asset_space ON public.knowledge_assets USING btree (organization_id, knowledge_space_id, updated_at DESC);


--
-- Name: idx_knowledge_asset_version_asset; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_asset_version_asset ON public.knowledge_asset_versions USING btree (organization_id, knowledge_asset_id, version_number DESC);


--
-- Name: idx_knowledge_asset_version_classification; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_asset_version_classification ON public.knowledge_asset_versions USING btree (organization_id, classification);


--
-- Name: idx_knowledge_asset_version_department; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_asset_version_department ON public.knowledge_asset_versions USING btree (organization_id, department_id);


--
-- Name: idx_knowledge_asset_version_space; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_asset_version_space ON public.knowledge_asset_versions USING btree (organization_id, knowledge_space_id, status);


--
-- Name: idx_knowledge_asset_version_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_asset_version_status ON public.knowledge_asset_versions USING btree (organization_id, status, updated_at DESC);


--
-- Name: idx_knowledge_chunk_embedding_1536_hnsw; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_chunk_embedding_1536_hnsw ON public.knowledge_chunks USING hnsw (((embedding)::public.vector(1536)) public.vector_cosine_ops) WHERE (active AND (embedding_dimensions = 1536));


--
-- Name: idx_knowledge_chunk_revision; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_chunk_revision ON public.knowledge_chunks USING btree (organization_id, source_revision_id, active);


--
-- Name: idx_knowledge_chunk_search_vector_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_chunk_search_vector_active ON public.knowledge_chunks USING gin (search_vector) WHERE active;


--
-- Name: idx_knowledge_space_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_space_active ON public.knowledge_spaces USING btree (organization_id, active, name);


--
-- Name: idx_normalized_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_normalized_status ON public.normalized_records USING btree (organization_id, status);


--
-- Name: idx_permission_audit_actor_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_audit_actor_time ON public.permission_audit_events USING btree (organization_id, actor_user_id, occurred_at DESC);


--
-- Name: idx_permission_audit_chunk; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_audit_chunk ON public.permission_audit_events USING btree (organization_id, knowledge_chunk_id) WHERE (knowledge_chunk_id IS NOT NULL);


--
-- Name: idx_permission_audit_org_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_audit_org_time ON public.permission_audit_events USING btree (organization_id, occurred_at DESC);


--
-- Name: idx_permission_audit_resource; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_audit_resource ON public.permission_audit_events USING btree (organization_id, resource_type, resource_id);


--
-- Name: idx_projection_content_visible; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projection_content_visible ON public.projection_content_records USING btree (batch_id, organization_id, knowledge_asset_id, record_id);


--
-- Name: idx_projection_graph_entity_revision; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projection_graph_entity_revision ON public.projection_graph_entity_contributions USING btree (batch_id, source_revision_id);


--
-- Name: idx_projection_graph_entity_visible; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projection_graph_entity_visible ON public.projection_graph_entity_contributions USING btree (batch_id, organization_id, knowledge_asset_id, entity_id);


--
-- Name: idx_projection_graph_relation_revision; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projection_graph_relation_revision ON public.projection_graph_relation_contributions USING btree (batch_id, source_revision_id);


--
-- Name: idx_projection_graph_relation_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projection_graph_relation_source ON public.projection_graph_relations USING btree (batch_id, source_entity_id);


--
-- Name: idx_projection_graph_relation_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projection_graph_relation_target ON public.projection_graph_relations USING btree (batch_id, target_entity_id);


--
-- Name: idx_projection_graph_relation_visible; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projection_graph_relation_visible ON public.projection_graph_relation_contributions USING btree (batch_id, organization_id, knowledge_asset_id, relation_id);


--
-- Name: idx_projection_lexical_search; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projection_lexical_search ON public.projection_lexical_documents USING gin (search_vector);


--
-- Name: idx_projection_lexical_visible; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projection_lexical_visible ON public.projection_lexical_documents USING btree (batch_id, organization_id, knowledge_asset_id, document_id);


--
-- Name: idx_projection_vector_records_1536_hnsw_cosine; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projection_vector_records_1536_hnsw_cosine ON public.projection_vector_records USING hnsw (((embedding)::public.vector(1536)) public.vector_cosine_ops) WITH (m='16', ef_construction='64') WHERE (dimensions = 1536);


--
-- Name: idx_projection_vector_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projection_vector_subject ON public.projection_vector_records USING btree (batch_id, subject_id);


--
-- Name: idx_projection_vector_visible; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projection_vector_visible ON public.projection_vector_records USING btree (batch_id, organization_id, knowledge_asset_id, embedding_profile_id, vector_kind);


--
-- Name: idx_raw_source_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_raw_source_lookup ON public.raw_source_objects USING btree (organization_id, source_system, source_connection_key, external_object_id);


--
-- Name: idx_raw_source_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_raw_source_status ON public.raw_source_objects USING btree (organization_id, status);


--
-- Name: idx_source_acl_group_member_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_source_acl_group_member_member ON public.source_acl_group_members USING btree (organization_id, member_principal_id);


--
-- Name: idx_source_acl_head_current; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_source_acl_head_current ON public.source_acl_heads USING btree (organization_id, current_snapshot_id);


--
-- Name: idx_source_acl_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_source_acl_principal ON public.source_acl_entries USING btree (organization_id, principal_type, principal_key, source_acl_snapshot_id);


--
-- Name: idx_source_acl_seal_organization; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_source_acl_seal_organization ON public.source_acl_snapshot_seals USING btree (organization_id, source_acl_snapshot_id);


--
-- Name: idx_source_ingestion_claim; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_source_ingestion_claim ON public.source_ingestion_jobs USING btree (status, available_at, created_at) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying])::text[]));


--
-- Name: idx_source_object_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_source_object_owner ON public.source_objects USING btree (organization_id, created_by_user_id, updated_at DESC);


--
-- Name: idx_source_object_space; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_source_object_space ON public.source_objects USING btree (organization_id, knowledge_space_id, updated_at DESC);


--
-- Name: idx_source_principal_mapping_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_source_principal_mapping_user ON public.source_principal_mappings USING btree (organization_id, app_user_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: idx_source_revision_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_source_revision_status ON public.source_revisions USING btree (organization_id, status, updated_at DESC);


--
-- Name: idx_user_invitation_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_invitation_lookup ON public.user_invitations USING btree (lower((email)::text)) WHERE ((accepted_at IS NULL) AND (revoked_at IS NULL));


--
-- Name: spring_session_ix1; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX spring_session_ix1 ON public.spring_session USING btree (session_id);


--
-- Name: spring_session_ix2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX spring_session_ix2 ON public.spring_session USING btree (expiry_time);


--
-- Name: spring_session_ix3; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX spring_session_ix3 ON public.spring_session USING btree (principal_name);


--
-- Name: uq_app_users_email_lower; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_app_users_email_lower ON public.app_users USING btree (lower((email)::text));


--
-- Name: uq_knowledge_asset_one_active_version; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_knowledge_asset_one_active_version ON public.knowledge_asset_versions USING btree (knowledge_asset_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: uq_projection_batches_active_idempotency; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_projection_batches_active_idempotency ON public.projection_batches USING btree (organization_id, workspace, collection_name, idempotency_key) WHERE ((status)::text <> 'ABORTED'::text);


--
-- Name: uq_source_principal_mapping_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_source_principal_mapping_active ON public.source_principal_mappings USING btree (source_principal_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: uq_user_invitation_open; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_user_invitation_open ON public.user_invitations USING btree (organization_id, lower((email)::text)) WHERE ((accepted_at IS NULL) AND (revoked_at IS NULL));


--
-- Name: permission_audit_events permission_audit_events_append_only; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER permission_audit_events_append_only BEFORE DELETE OR UPDATE OR TRUNCATE ON public.permission_audit_events FOR EACH STATEMENT EXECUTE FUNCTION public.reject_permission_audit_mutation();

ALTER TABLE public.permission_audit_events ENABLE ALWAYS TRIGGER permission_audit_events_append_only;


--
-- Name: source_acl_entries source_acl_entries_append_only; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER source_acl_entries_append_only BEFORE DELETE OR UPDATE OR TRUNCATE ON public.source_acl_entries FOR EACH STATEMENT EXECUTE FUNCTION public.reject_source_acl_evidence_mutation();

ALTER TABLE public.source_acl_entries ENABLE ALWAYS TRIGGER source_acl_entries_append_only;


--
-- Name: source_acl_entries source_acl_entries_reject_after_seal; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER source_acl_entries_reject_after_seal BEFORE INSERT ON public.source_acl_entries FOR EACH ROW EXECUTE FUNCTION public.reject_entry_insert_into_sealed_acl();


--
-- Name: source_acl_group_members source_acl_group_members_append_only; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER source_acl_group_members_append_only BEFORE DELETE OR UPDATE OR TRUNCATE ON public.source_acl_group_members FOR EACH STATEMENT EXECUTE FUNCTION public.reject_source_acl_evidence_mutation();


--
-- Name: source_acl_group_members source_acl_group_members_reject_after_seal; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER source_acl_group_members_reject_after_seal BEFORE INSERT ON public.source_acl_group_members FOR EACH ROW EXECUTE FUNCTION public.reject_entry_insert_into_sealed_acl();


--
-- Name: source_acl_snapshot_seals source_acl_snapshot_seal_validate; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER source_acl_snapshot_seal_validate BEFORE INSERT ON public.source_acl_snapshot_seals FOR EACH ROW EXECUTE FUNCTION public.validate_source_acl_seal();


--
-- Name: source_acl_snapshot_seals source_acl_snapshot_seals_append_only; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER source_acl_snapshot_seals_append_only BEFORE DELETE OR UPDATE OR TRUNCATE ON public.source_acl_snapshot_seals FOR EACH STATEMENT EXECUTE FUNCTION public.reject_source_acl_evidence_mutation();

ALTER TABLE public.source_acl_snapshot_seals ENABLE ALWAYS TRIGGER source_acl_snapshot_seals_append_only;


--
-- Name: source_acl_snapshots source_acl_snapshots_append_only; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER source_acl_snapshots_append_only BEFORE DELETE OR UPDATE OR TRUNCATE ON public.source_acl_snapshots FOR EACH STATEMENT EXECUTE FUNCTION public.reject_source_acl_evidence_mutation();

ALTER TABLE public.source_acl_snapshots ENABLE ALWAYS TRIGGER source_acl_snapshots_append_only;


--
-- Name: app_users app_users_department_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_users
    ADD CONSTRAINT app_users_department_id_fkey FOREIGN KEY (department_id) REFERENCES public.departments(id);


--
-- Name: app_users app_users_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_users
    ADD CONSTRAINT app_users_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: connector_crawl_attempts connector_crawl_attempts_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.connector_crawl_attempts
    ADD CONSTRAINT connector_crawl_attempts_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: connector_crawl_checkpoints connector_crawl_checkpoints_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.connector_crawl_checkpoints
    ADD CONSTRAINT connector_crawl_checkpoints_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: departments departments_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT departments_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: embedding_profiles embedding_profiles_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.embedding_profiles
    ADD CONSTRAINT embedding_profiles_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: evidence_blobs evidence_blobs_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evidence_blobs
    ADD CONSTRAINT evidence_blobs_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: external_identities external_identities_app_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.external_identities
    ADD CONSTRAINT external_identities_app_user_id_fkey FOREIGN KEY (app_user_id) REFERENCES public.app_users(id) ON DELETE CASCADE;


--
-- Name: graph_retrieval_cache_evidence fk_graph_cache_evidence_acl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_retrieval_cache_evidence
    ADD CONSTRAINT fk_graph_cache_evidence_acl FOREIGN KEY (acl_snapshot_id, organization_id, acl_generation) REFERENCES public.source_acl_snapshots(id, organization_id, acl_generation);


--
-- Name: graph_retrieval_cache_evidence fk_graph_cache_evidence_asset; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_retrieval_cache_evidence
    ADD CONSTRAINT fk_graph_cache_evidence_asset FOREIGN KEY (knowledge_asset_id, organization_id) REFERENCES public.knowledge_assets(id, organization_id);


--
-- Name: graph_retrieval_cache_evidence fk_graph_cache_evidence_revision; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_retrieval_cache_evidence
    ADD CONSTRAINT fk_graph_cache_evidence_revision FOREIGN KEY (source_revision_id, organization_id, knowledge_asset_id) REFERENCES public.source_revisions(id, organization_id, knowledge_asset_id);


--
-- Name: graph_curation_records fk_graph_curation_governing_acl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_curation_records
    ADD CONSTRAINT fk_graph_curation_governing_acl FOREIGN KEY (governing_acl_snapshot_id, organization_id, governing_acl_generation) REFERENCES public.source_acl_snapshots(id, organization_id, acl_generation);


--
-- Name: graph_curation_records fk_graph_curation_governing_asset; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_curation_records
    ADD CONSTRAINT fk_graph_curation_governing_asset FOREIGN KEY (governing_knowledge_asset_id, organization_id) REFERENCES public.knowledge_assets(id, organization_id);


--
-- Name: graph_curation_records fk_graph_curation_governing_revision; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_curation_records
    ADD CONSTRAINT fk_graph_curation_governing_revision FOREIGN KEY (governing_source_revision_id, organization_id, governing_knowledge_asset_id) REFERENCES public.source_revisions(id, organization_id, knowledge_asset_id);


--
-- Name: graph_index_jobs fk_graph_index_job_asset; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_index_jobs
    ADD CONSTRAINT fk_graph_index_job_asset FOREIGN KEY (knowledge_asset_id, organization_id) REFERENCES public.knowledge_assets(id, organization_id);


--
-- Name: graph_index_jobs fk_graph_index_job_asset_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_index_jobs
    ADD CONSTRAINT fk_graph_index_job_asset_version FOREIGN KEY (knowledge_asset_version_id, organization_id, knowledge_asset_id) REFERENCES public.knowledge_asset_versions(id, organization_id, knowledge_asset_id);


--
-- Name: graph_index_jobs fk_graph_index_job_processing_profile; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_index_jobs
    ADD CONSTRAINT fk_graph_index_job_processing_profile FOREIGN KEY (graph_processing_profile_id) REFERENCES public.graph_processing_profiles(id);


--
-- Name: graph_index_jobs fk_graph_index_job_source_revision; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_index_jobs
    ADD CONSTRAINT fk_graph_index_job_source_revision FOREIGN KEY (source_revision_id, organization_id, knowledge_asset_id, knowledge_asset_version_id) REFERENCES public.source_revisions(id, organization_id, knowledge_asset_id, knowledge_asset_version_id);


--
-- Name: knowledge_assets fk_knowledge_asset_current_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_assets
    ADD CONSTRAINT fk_knowledge_asset_current_version FOREIGN KEY (current_version_id, organization_id, id) REFERENCES public.knowledge_asset_versions(id, organization_id, knowledge_asset_id);


--
-- Name: knowledge_asset_evidence_links fk_knowledge_asset_evidence_acl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_evidence_links
    ADD CONSTRAINT fk_knowledge_asset_evidence_acl FOREIGN KEY (source_acl_snapshot_id, organization_id) REFERENCES public.source_acl_snapshots(id, organization_id);


--
-- Name: knowledge_asset_evidence_links fk_knowledge_asset_evidence_revision; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_evidence_links
    ADD CONSTRAINT fk_knowledge_asset_evidence_revision FOREIGN KEY (source_revision_id, organization_id) REFERENCES public.source_revisions(id, organization_id);


--
-- Name: knowledge_asset_evidence_links fk_knowledge_asset_evidence_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_evidence_links
    ADD CONSTRAINT fk_knowledge_asset_evidence_version FOREIGN KEY (knowledge_asset_version_id, organization_id) REFERENCES public.knowledge_asset_versions(id, organization_id) ON DELETE CASCADE;


--
-- Name: knowledge_asset_publication_outbox fk_knowledge_asset_publication_asset; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_publication_outbox
    ADD CONSTRAINT fk_knowledge_asset_publication_asset FOREIGN KEY (knowledge_asset_id, organization_id) REFERENCES public.knowledge_assets(id, organization_id);


--
-- Name: knowledge_asset_publication_outbox fk_knowledge_asset_publication_embedding_profile; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_publication_outbox
    ADD CONSTRAINT fk_knowledge_asset_publication_embedding_profile FOREIGN KEY (embedding_profile_id, organization_id, embedding_dimensions) REFERENCES public.embedding_profiles(id, organization_id, dimensions);


--
-- Name: knowledge_asset_publication_outbox fk_knowledge_asset_publication_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_publication_outbox
    ADD CONSTRAINT fk_knowledge_asset_publication_owner FOREIGN KEY (owner_user_id, organization_id) REFERENCES public.app_users(id, organization_id);


--
-- Name: knowledge_asset_publication_outbox fk_knowledge_asset_publication_revision; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_publication_outbox
    ADD CONSTRAINT fk_knowledge_asset_publication_revision FOREIGN KEY (source_revision_id, organization_id, source_object_id) REFERENCES public.source_revisions(id, organization_id, source_object_id);


--
-- Name: knowledge_asset_publication_outbox fk_knowledge_asset_publication_space_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_publication_outbox
    ADD CONSTRAINT fk_knowledge_asset_publication_space_organization FOREIGN KEY (knowledge_space_id, organization_id) REFERENCES public.knowledge_spaces(id, organization_id);


--
-- Name: knowledge_asset_publication_outbox fk_knowledge_asset_publication_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_publication_outbox
    ADD CONSTRAINT fk_knowledge_asset_publication_version FOREIGN KEY (knowledge_asset_version_id, organization_id, knowledge_asset_id) REFERENCES public.knowledge_asset_versions(id, organization_id, knowledge_asset_id);


--
-- Name: knowledge_assets fk_knowledge_asset_source_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_assets
    ADD CONSTRAINT fk_knowledge_asset_source_organization FOREIGN KEY (source_object_id, organization_id) REFERENCES public.source_objects(id, organization_id);


--
-- Name: knowledge_asset_versions fk_knowledge_asset_space_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_versions
    ADD CONSTRAINT fk_knowledge_asset_space_organization FOREIGN KEY (knowledge_space_id, organization_id) REFERENCES public.knowledge_spaces(id, organization_id);


--
-- Name: knowledge_assets fk_knowledge_asset_space_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_assets
    ADD CONSTRAINT fk_knowledge_asset_space_organization FOREIGN KEY (knowledge_space_id, organization_id) REFERENCES public.knowledge_spaces(id, organization_id);


--
-- Name: knowledge_asset_versions fk_knowledge_asset_version_asset; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_versions
    ADD CONSTRAINT fk_knowledge_asset_version_asset FOREIGN KEY (knowledge_asset_id, organization_id) REFERENCES public.knowledge_assets(id, organization_id);


--
-- Name: knowledge_asset_versions fk_knowledge_asset_version_revision; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_versions
    ADD CONSTRAINT fk_knowledge_asset_version_revision FOREIGN KEY (source_revision_id, organization_id) REFERENCES public.source_revisions(id, organization_id);


--
-- Name: knowledge_chunks fk_knowledge_chunk_asset_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunks
    ADD CONSTRAINT fk_knowledge_chunk_asset_organization FOREIGN KEY (knowledge_asset_id, organization_id) REFERENCES public.knowledge_assets(id, organization_id);


--
-- Name: knowledge_chunks fk_knowledge_chunk_asset_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunks
    ADD CONSTRAINT fk_knowledge_chunk_asset_version FOREIGN KEY (knowledge_asset_version_id, organization_id, knowledge_asset_id) REFERENCES public.knowledge_asset_versions(id, organization_id, knowledge_asset_id);


--
-- Name: knowledge_chunks fk_knowledge_chunk_embedding_profile; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunks
    ADD CONSTRAINT fk_knowledge_chunk_embedding_profile FOREIGN KEY (embedding_profile_id, organization_id, embedding_dimensions) REFERENCES public.embedding_profiles(id, organization_id, dimensions);


--
-- Name: knowledge_chunks fk_knowledge_chunk_revision_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunks
    ADD CONSTRAINT fk_knowledge_chunk_revision_organization FOREIGN KEY (source_revision_id, organization_id) REFERENCES public.source_revisions(id, organization_id);


--
-- Name: knowledge_chunks fk_knowledge_chunk_source_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunks
    ADD CONSTRAINT fk_knowledge_chunk_source_organization FOREIGN KEY (source_object_id, organization_id) REFERENCES public.source_objects(id, organization_id);


--
-- Name: knowledge_asset_versions fk_knowledge_department_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_versions
    ADD CONSTRAINT fk_knowledge_department_organization FOREIGN KEY (department_id, organization_id) REFERENCES public.departments(id, organization_id);


--
-- Name: knowledge_asset_versions fk_knowledge_normalized_chain; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_versions
    ADD CONSTRAINT fk_knowledge_normalized_chain FOREIGN KEY (normalized_record_id, organization_id, raw_source_object_id, source_acl_snapshot_id) REFERENCES public.normalized_records(id, organization_id, raw_source_object_id, source_acl_snapshot_id);


--
-- Name: knowledge_spaces fk_knowledge_space_department_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_spaces
    ADD CONSTRAINT fk_knowledge_space_department_organization FOREIGN KEY (department_id, organization_id) REFERENCES public.departments(id, organization_id);


--
-- Name: normalized_records fk_normalized_department_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.normalized_records
    ADD CONSTRAINT fk_normalized_department_organization FOREIGN KEY (department_id, organization_id) REFERENCES public.departments(id, organization_id);


--
-- Name: normalized_records fk_normalized_snapshot_chain; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.normalized_records
    ADD CONSTRAINT fk_normalized_snapshot_chain FOREIGN KEY (source_acl_snapshot_id, organization_id, raw_source_object_id) REFERENCES public.source_acl_snapshots(id, organization_id, raw_source_object_id);


--
-- Name: normalized_records fk_normalized_snapshot_seal; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.normalized_records
    ADD CONSTRAINT fk_normalized_snapshot_seal FOREIGN KEY (source_acl_snapshot_id, organization_id) REFERENCES public.source_acl_snapshot_seals(source_acl_snapshot_id, organization_id);


--
-- Name: permission_audit_events fk_permission_audit_current_acl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_audit_events
    ADD CONSTRAINT fk_permission_audit_current_acl FOREIGN KEY (current_acl_snapshot_id, organization_id) REFERENCES public.source_acl_snapshots(id, organization_id);


--
-- Name: permission_audit_events fk_permission_audit_ingestion_acl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_audit_events
    ADD CONSTRAINT fk_permission_audit_ingestion_acl FOREIGN KEY (ingestion_acl_snapshot_id, organization_id) REFERENCES public.source_acl_snapshots(id, organization_id);


--
-- Name: projection_graph_entity_contributions fk_projection_graph_entity_identity; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_graph_entity_contributions
    ADD CONSTRAINT fk_projection_graph_entity_identity FOREIGN KEY (batch_id, entity_id) REFERENCES public.projection_graph_entities(batch_id, entity_id) ON DELETE CASCADE;


--
-- Name: projection_graph_relation_contributions fk_projection_graph_relation_identity; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_graph_relation_contributions
    ADD CONSTRAINT fk_projection_graph_relation_identity FOREIGN KEY (batch_id, relation_id) REFERENCES public.projection_graph_relations(batch_id, relation_id) ON DELETE CASCADE;


--
-- Name: raw_source_objects fk_raw_source_department_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.raw_source_objects
    ADD CONSTRAINT fk_raw_source_department_organization FOREIGN KEY (department_id, organization_id) REFERENCES public.departments(id, organization_id);


--
-- Name: source_acl_entries fk_source_acl_entry_snapshot_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_entries
    ADD CONSTRAINT fk_source_acl_entry_snapshot_organization FOREIGN KEY (source_acl_snapshot_id, organization_id) REFERENCES public.source_acl_snapshots(id, organization_id);


--
-- Name: source_acl_group_members fk_source_acl_group_member_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_group_members
    ADD CONSTRAINT fk_source_acl_group_member_group FOREIGN KEY (group_principal_id, organization_id, group_principal_kind) REFERENCES public.source_principals(id, organization_id, kind);


--
-- Name: source_acl_group_members fk_source_acl_group_member_member; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_group_members
    ADD CONSTRAINT fk_source_acl_group_member_member FOREIGN KEY (member_principal_id, organization_id, member_principal_kind) REFERENCES public.source_principals(id, organization_id, kind);


--
-- Name: source_acl_group_members fk_source_acl_group_member_snapshot; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_group_members
    ADD CONSTRAINT fk_source_acl_group_member_snapshot FOREIGN KEY (source_acl_snapshot_id, organization_id) REFERENCES public.source_acl_snapshots(id, organization_id);


--
-- Name: source_acl_heads fk_source_acl_head_current_seal; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_heads
    ADD CONSTRAINT fk_source_acl_head_current_seal FOREIGN KEY (current_snapshot_id, organization_id) REFERENCES public.source_acl_snapshot_seals(source_acl_snapshot_id, organization_id);


--
-- Name: source_acl_heads fk_source_acl_head_raw_identity; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_heads
    ADD CONSTRAINT fk_source_acl_head_raw_identity FOREIGN KEY (current_raw_source_object_id, organization_id, source_system, source_connection_key, external_object_id) REFERENCES public.raw_source_objects(id, organization_id, source_system, source_connection_key, external_object_id);


--
-- Name: source_acl_heads fk_source_acl_head_snapshot_chain; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_heads
    ADD CONSTRAINT fk_source_acl_head_snapshot_chain FOREIGN KEY (current_snapshot_id, organization_id, current_raw_source_object_id, acl_generation) REFERENCES public.source_acl_snapshots(id, organization_id, raw_source_object_id, acl_generation);


--
-- Name: source_acl_snapshots fk_source_acl_raw_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_snapshots
    ADD CONSTRAINT fk_source_acl_raw_organization FOREIGN KEY (raw_source_object_id, organization_id) REFERENCES public.raw_source_objects(id, organization_id);


--
-- Name: source_acl_snapshot_seals fk_source_acl_seal_snapshot_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_snapshot_seals
    ADD CONSTRAINT fk_source_acl_seal_snapshot_organization FOREIGN KEY (source_acl_snapshot_id, organization_id) REFERENCES public.source_acl_snapshots(id, organization_id);


--
-- Name: source_connections fk_source_connection_actor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_connections
    ADD CONSTRAINT fk_source_connection_actor FOREIGN KEY (actor_user_id, organization_id) REFERENCES public.app_users(id, organization_id);


--
-- Name: source_connections fk_source_connection_configured_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_connections
    ADD CONSTRAINT fk_source_connection_configured_by FOREIGN KEY (crawl_configured_by_user_id, organization_id) REFERENCES public.app_users(id, organization_id);


--
-- Name: source_connection_credentials fk_source_connection_credential_set_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_connection_credentials
    ADD CONSTRAINT fk_source_connection_credential_set_by FOREIGN KEY (set_by_user_id, organization_id) REFERENCES public.app_users(id, organization_id);


--
-- Name: source_connections fk_source_connection_decided_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_connections
    ADD CONSTRAINT fk_source_connection_decided_by FOREIGN KEY (trust_decided_by_user_id, organization_id) REFERENCES public.app_users(id, organization_id);


--
-- Name: source_ingestion_jobs fk_source_ingestion_job_revision_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_ingestion_jobs
    ADD CONSTRAINT fk_source_ingestion_job_revision_organization FOREIGN KEY (source_revision_id, organization_id) REFERENCES public.source_revisions(id, organization_id);


--
-- Name: source_objects fk_source_object_current_revision; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_objects
    ADD CONSTRAINT fk_source_object_current_revision FOREIGN KEY (current_revision_id, organization_id, id) REFERENCES public.source_revisions(id, organization_id, source_object_id);


--
-- Name: source_objects fk_source_object_department_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_objects
    ADD CONSTRAINT fk_source_object_department_organization FOREIGN KEY (department_id, organization_id) REFERENCES public.departments(id, organization_id);


--
-- Name: source_objects fk_source_object_latest_revision; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_objects
    ADD CONSTRAINT fk_source_object_latest_revision FOREIGN KEY (latest_revision_id, organization_id, id) REFERENCES public.source_revisions(id, organization_id, source_object_id);


--
-- Name: source_objects fk_source_object_space_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_objects
    ADD CONSTRAINT fk_source_object_space_organization FOREIGN KEY (knowledge_space_id, organization_id) REFERENCES public.knowledge_spaces(id, organization_id);


--
-- Name: source_principal_mappings fk_source_principal_mapping_principal_kind; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_principal_mappings
    ADD CONSTRAINT fk_source_principal_mapping_principal_kind FOREIGN KEY (source_principal_id, organization_id, source_principal_kind) REFERENCES public.source_principals(id, organization_id, kind);


--
-- Name: source_principal_mappings fk_source_principal_mapping_user_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_principal_mappings
    ADD CONSTRAINT fk_source_principal_mapping_user_organization FOREIGN KEY (app_user_id, organization_id) REFERENCES public.app_users(id, organization_id);


--
-- Name: source_revisions fk_source_revision_blob_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT fk_source_revision_blob_organization FOREIGN KEY (evidence_blob_id, organization_id) REFERENCES public.evidence_blobs(id, organization_id);


--
-- Name: source_revisions fk_source_revision_department_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT fk_source_revision_department_organization FOREIGN KEY (department_id, organization_id) REFERENCES public.departments(id, organization_id);


--
-- Name: source_revisions fk_source_revision_embedding_profile; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT fk_source_revision_embedding_profile FOREIGN KEY (embedding_profile_id, organization_id, embedding_dimensions) REFERENCES public.embedding_profiles(id, organization_id, dimensions);


--
-- Name: source_revisions fk_source_revision_knowledge_asset; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT fk_source_revision_knowledge_asset FOREIGN KEY (knowledge_asset_id, organization_id) REFERENCES public.knowledge_assets(id, organization_id);


--
-- Name: source_revisions fk_source_revision_knowledge_asset_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT fk_source_revision_knowledge_asset_version FOREIGN KEY (knowledge_asset_version_id, organization_id, knowledge_asset_id) REFERENCES public.knowledge_asset_versions(id, organization_id, knowledge_asset_id);


--
-- Name: source_revisions fk_source_revision_object_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT fk_source_revision_object_organization FOREIGN KEY (source_object_id, organization_id) REFERENCES public.source_objects(id, organization_id);


--
-- Name: source_revisions fk_source_revision_space_organization; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT fk_source_revision_space_organization FOREIGN KEY (knowledge_space_id, organization_id) REFERENCES public.knowledge_spaces(id, organization_id);


--
-- Name: graph_curation_records graph_curation_records_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_curation_records
    ADD CONSTRAINT graph_curation_records_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: graph_index_jobs graph_index_jobs_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_index_jobs
    ADD CONSTRAINT graph_index_jobs_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: graph_model_invocation_cache graph_model_invocation_cache_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_model_invocation_cache
    ADD CONSTRAINT graph_model_invocation_cache_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: graph_retrieval_cache_evidence graph_retrieval_cache_evidence_cache_entry_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_retrieval_cache_evidence
    ADD CONSTRAINT graph_retrieval_cache_evidence_cache_entry_id_fkey FOREIGN KEY (cache_entry_id) REFERENCES public.graph_retrieval_result_cache(id) ON DELETE CASCADE;


--
-- Name: graph_retrieval_result_cache graph_retrieval_result_cache_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.graph_retrieval_result_cache
    ADD CONSTRAINT graph_retrieval_result_cache_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: knowledge_asset_evidence_links knowledge_asset_evidence_links_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_evidence_links
    ADD CONSTRAINT knowledge_asset_evidence_links_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: knowledge_asset_publication_outbox knowledge_asset_publication_outbox_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_publication_outbox
    ADD CONSTRAINT knowledge_asset_publication_outbox_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: knowledge_asset_versions knowledge_assets_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_asset_versions
    ADD CONSTRAINT knowledge_assets_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: knowledge_assets knowledge_assets_organization_id_fkey1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_assets
    ADD CONSTRAINT knowledge_assets_organization_id_fkey1 FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: knowledge_chunks knowledge_chunks_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunks
    ADD CONSTRAINT knowledge_chunks_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: knowledge_spaces knowledge_spaces_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_spaces
    ADD CONSTRAINT knowledge_spaces_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: normalized_records normalized_records_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.normalized_records
    ADD CONSTRAINT normalized_records_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: permission_audit_events permission_audit_events_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_audit_events
    ADD CONSTRAINT permission_audit_events_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: projection_batch_receipts projection_batch_receipts_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_batch_receipts
    ADD CONSTRAINT projection_batch_receipts_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES public.projection_batches(batch_id) ON DELETE CASCADE;


--
-- Name: projection_batches projection_batches_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_batches
    ADD CONSTRAINT projection_batches_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: projection_content_records projection_content_records_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_content_records
    ADD CONSTRAINT projection_content_records_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES public.projection_batches(batch_id) ON DELETE CASCADE;


--
-- Name: projection_graph_entities projection_graph_entities_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_graph_entities
    ADD CONSTRAINT projection_graph_entities_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES public.projection_batches(batch_id) ON DELETE CASCADE;


--
-- Name: projection_graph_entity_contributions projection_graph_entity_contributions_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_graph_entity_contributions
    ADD CONSTRAINT projection_graph_entity_contributions_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES public.projection_batches(batch_id) ON DELETE CASCADE;


--
-- Name: projection_graph_relation_contributions projection_graph_relation_contributions_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_graph_relation_contributions
    ADD CONSTRAINT projection_graph_relation_contributions_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES public.projection_batches(batch_id) ON DELETE CASCADE;


--
-- Name: projection_graph_relations projection_graph_relations_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_graph_relations
    ADD CONSTRAINT projection_graph_relations_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES public.projection_batches(batch_id) ON DELETE CASCADE;


--
-- Name: projection_lexical_documents projection_lexical_documents_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_lexical_documents
    ADD CONSTRAINT projection_lexical_documents_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES public.projection_batches(batch_id) ON DELETE CASCADE;


--
-- Name: projection_namespace_heads projection_namespace_heads_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_namespace_heads
    ADD CONSTRAINT projection_namespace_heads_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES public.projection_publications(batch_id);


--
-- Name: projection_publications projection_publications_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_publications
    ADD CONSTRAINT projection_publications_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES public.projection_batches(batch_id);


--
-- Name: projection_stage_initializations projection_stage_initializations_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_stage_initializations
    ADD CONSTRAINT projection_stage_initializations_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES public.projection_batches(batch_id) ON DELETE CASCADE;


--
-- Name: projection_vector_records projection_vector_records_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projection_vector_records
    ADD CONSTRAINT projection_vector_records_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES public.projection_batches(batch_id) ON DELETE CASCADE;


--
-- Name: raw_source_objects raw_source_objects_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.raw_source_objects
    ADD CONSTRAINT raw_source_objects_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: source_acl_entries source_acl_entries_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_entries
    ADD CONSTRAINT source_acl_entries_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: source_acl_group_members source_acl_group_members_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_group_members
    ADD CONSTRAINT source_acl_group_members_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: source_acl_heads source_acl_heads_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_heads
    ADD CONSTRAINT source_acl_heads_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: source_acl_snapshot_seals source_acl_snapshot_seals_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_snapshot_seals
    ADD CONSTRAINT source_acl_snapshot_seals_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: source_acl_snapshots source_acl_snapshots_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_acl_snapshots
    ADD CONSTRAINT source_acl_snapshots_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: source_connection_credentials source_connection_credentials_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_connection_credentials
    ADD CONSTRAINT source_connection_credentials_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: source_connection_credentials source_connection_credentials_source_connection_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_connection_credentials
    ADD CONSTRAINT source_connection_credentials_source_connection_id_fkey FOREIGN KEY (source_connection_id) REFERENCES public.source_connections(id) ON DELETE CASCADE;


--
-- Name: source_connections source_connections_knowledge_space_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_connections
    ADD CONSTRAINT source_connections_knowledge_space_id_fkey FOREIGN KEY (knowledge_space_id) REFERENCES public.knowledge_spaces(id);


--
-- Name: source_connections source_connections_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_connections
    ADD CONSTRAINT source_connections_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: source_ingestion_jobs source_ingestion_jobs_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_ingestion_jobs
    ADD CONSTRAINT source_ingestion_jobs_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: source_objects source_objects_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_objects
    ADD CONSTRAINT source_objects_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.app_users(id);


--
-- Name: source_objects source_objects_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_objects
    ADD CONSTRAINT source_objects_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: source_principal_mappings source_principal_mappings_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_principal_mappings
    ADD CONSTRAINT source_principal_mappings_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: source_principals source_principals_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_principals
    ADD CONSTRAINT source_principals_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: source_revisions source_revisions_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT source_revisions_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.app_users(id);


--
-- Name: source_revisions source_revisions_normalized_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT source_revisions_normalized_record_id_fkey FOREIGN KEY (normalized_record_id) REFERENCES public.normalized_records(id);


--
-- Name: source_revisions source_revisions_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT source_revisions_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- Name: source_revisions source_revisions_raw_source_object_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_revisions
    ADD CONSTRAINT source_revisions_raw_source_object_id_fkey FOREIGN KEY (raw_source_object_id) REFERENCES public.raw_source_objects(id);


--
-- Name: spring_session_attributes spring_session_attributes_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id) REFERENCES public.spring_session(primary_id) ON DELETE CASCADE;


--
-- Name: user_invitations user_invitations_accepted_app_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_invitations
    ADD CONSTRAINT user_invitations_accepted_app_user_id_fkey FOREIGN KEY (accepted_app_user_id) REFERENCES public.app_users(id);


--
-- Name: user_invitations user_invitations_department_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_invitations
    ADD CONSTRAINT user_invitations_department_id_fkey FOREIGN KEY (department_id) REFERENCES public.departments(id);


--
-- Name: user_invitations user_invitations_invited_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_invitations
    ADD CONSTRAINT user_invitations_invited_by_user_id_fkey FOREIGN KEY (invited_by_user_id) REFERENCES public.app_users(id);


--
-- Name: user_invitations user_invitations_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_invitations
    ADD CONSTRAINT user_invitations_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id);


--
-- PostgreSQL database dump complete
--
