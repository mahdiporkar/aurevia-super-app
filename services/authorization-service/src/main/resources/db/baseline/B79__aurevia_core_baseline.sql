-- Aurevia Core baseline (generated; do not edit by hand).
-- Equivalent to the versioned chain V1..V79 with every development/demo row removed and the
-- OpenFGA outbox regenerated for Core relationships only. Flyway applies this file INSTEAD of
-- V1..V79 on an empty database (fresh installation); databases that already carry the chain
-- ignore it. Regenerate with: node tools/generate-core-baseline.mjs
--
-- Contains: schema, actions, the Aurevia root/Admin/Reports application resources, the Admin Panel
-- (ADMIN panel + published MF manifests), the Superset integration root, platform roles and their
-- grants, the public-iam-forward outbound profile and schema_version markers.
-- Contains NO users, demo panels, demo resources, demo grants, demo routes or demo Superset assets.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

--
-- PostgreSQL database dump
--

-- Dumped from database version 17.6
-- Dumped by pg_dump version 17.6

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

--
-- Name: access_group_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE access_group_type AS ENUM (
    'DIRECTORY',
    'CALCULATED',
    'MANUAL'
);

--
-- Name: directory_source; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE directory_source AS ENUM (
    'ACTIVE_DIRECTORY'
);

--
-- Name: lifecycle_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE lifecycle_status AS ENUM (
    'ACTIVE',
    'INACTIVE',
    'ARCHIVED',
    'DRAFT',
    'DEPRECATED',
    'DISABLED'
);

--
-- Name: membership_source_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE membership_source_type AS ENUM (
    'OU_RULE',
    'LDAP_ATTRIBUTE',
    'MANUAL'
);

--
-- Name: ou_match_mode; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE ou_match_mode AS ENUM (
    'EXACT',
    'SUBTREE'
);

--
-- Name: outbound_connection_kind; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE outbound_connection_kind AS ENUM (
    'LEGACY_TOKEN'
);

--
-- Name: projection_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE projection_status AS ENUM (
    'PENDING',
    'APPLIED',
    'RETRYING',
    'FAILED',
    'REVOKED'
);

--
-- Name: recalculation_job_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE recalculation_job_status AS ENUM (
    'PENDING',
    'RUNNING',
    'COMPLETED',
    'FAILED'
);

--
-- Name: resource_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE resource_type AS ENUM (
    'APPLICATION',
    'MODULE',
    'PAGE',
    'UI_COMPONENT',
    'BUSINESS_RESOURCE',
    'EXTERNAL_RESOURCE',
    'API_RESOURCE',
    'DATA_RESOURCE',
    'DATA_GOVERNANCE_RESOURCE',
    'FIELD'
);

--
-- Name: rule_combiner; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE rule_combiner AS ENUM (
    'ANY_OF',
    'ALL_OF'
);

--
-- Name: subject_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE subject_type AS ENUM (
    'USER',
    'GROUP',
    'ROLE',
    'ACCESS_GROUP'
);

--
-- Name: superset_zone; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE superset_zone AS ENUM (
    'PUBLIC',
    'OPERATION'
);

--
-- Name: sync_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE sync_status AS ENUM (
    'PENDING',
    'APPLIED',
    'FAILED',
    'REVOKING',
    'DRIFTED'
);

--
-- Name: aurevia_subject_key(text, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION aurevia_subject_key(p_issuer text, p_subject text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
  SELECT 'v1_' || encode(digest(
    convert_to(trim(p_issuer), 'UTF8') || decode('00', 'hex') ||
    convert_to(trim(p_subject), 'UTF8'), 'sha256'), 'hex')
$$;

--
-- Name: access_group; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE access_group (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(160) NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(1000),
    group_type access_group_type DEFAULT 'CALCULATED'::access_group_type NOT NULL,
    rule_combiner rule_combiner DEFAULT 'ANY_OF'::rule_combiner NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_by character varying(500) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT access_group_code_check CHECK (((code)::text ~ '^[A-Z][A-Z0-9_]{2,159}$'::text))
);

--
-- Name: access_group_ou_rule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE access_group_ou_rule (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    access_group_id uuid NOT NULL,
    ou_id uuid NOT NULL,
    match_mode ou_match_mode NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_by character varying(500) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

--
-- Name: access_group_role_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE access_group_role_assignment (
    access_group_id uuid NOT NULL,
    role_id uuid NOT NULL,
    expires_at timestamp with time zone,
    assigned_by character varying(500) NOT NULL,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

--
-- Name: action; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE action (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    action_key character varying(100) NOT NULL,
    name_fa character varying(255) NOT NULL,
    name_en character varying(255) NOT NULL
);

--
-- Name: api_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE api_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_time timestamp with time zone NOT NULL,
    user_id character varying(500),
    actor_type character varying(80),
    service_name character varying(160) NOT NULL,
    http_method character varying(16) NOT NULL,
    route_template character varying(1000) NOT NULL,
    status_code integer NOT NULL,
    duration_ms bigint NOT NULL,
    source_ip character varying(128),
    user_agent character varying(1000),
    correlation_id character varying(128) NOT NULL,
    request_size_bytes bigint,
    response_size_bytes bigint,
    authorization_result character varying(20),
    resource_type character varying(100),
    resource_id character varying(500),
    business_action character varying(100),
    openfga_duration_ms bigint,
    database_duration_ms bigint,
    redis_duration_ms bigint,
    downstream_duration_ms bigint,
    error_code character varying(160),
    error_type character varying(500),
    error_response_body text,
    error_response_redacted boolean DEFAULT false NOT NULL,
    error_response_truncated boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT api_log_check CHECK (((error_response_body IS NULL) OR (status_code >= 400))),
    CONSTRAINT api_log_duration_ms_check CHECK ((duration_ms >= 0)),
    CONSTRAINT api_log_status_code_check CHECK (((status_code >= 100) AND (status_code <= 599)))
);

--
-- Name: app_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE app_user (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    issuer character varying(255) NOT NULL,
    external_id character varying(255) NOT NULL,
    username character varying(255) NOT NULL,
    display_name character varying(255),
    email character varying(320),
    status lifecycle_status DEFAULT 'ACTIVE'::lifecycle_status NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    membership_version bigint DEFAULT 0 NOT NULL,
    directory_attributes jsonb DEFAULT '{}'::jsonb NOT NULL,
    directory_external_id character varying(512),
    subject_key character varying(67) GENERATED ALWAYS AS (aurevia_subject_key((issuer)::text, (external_id)::text)) STORED,
    canonical_user_id character varying(80) DEFAULT ('usr_'::text || replace((gen_random_uuid())::text, '-'::text, ''::text)) NOT NULL
);

--
-- Name: application_group_grant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE application_group_grant (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    application_id uuid NOT NULL,
    access_group_id uuid NOT NULL,
    relation character varying(40) DEFAULT 'VIEWER'::character varying NOT NULL,
    status projection_status DEFAULT 'PENDING'::projection_status NOT NULL,
    granted_by character varying(500) NOT NULL,
    granted_at timestamp with time zone DEFAULT now() NOT NULL,
    revoked_by character varying(500),
    revoked_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT application_group_grant_relation_check CHECK (((relation)::text = 'VIEWER'::text))
);

--
-- Name: application_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE application_role (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    role_key character varying(160) NOT NULL,
    name_fa character varying(255) NOT NULL,
    name_en character varying(255) NOT NULL,
    status lifecycle_status DEFAULT 'ACTIVE'::lifecycle_status NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: audit_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE audit_event (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    actor_key character varying(500) NOT NULL,
    event_type character varying(160) NOT NULL,
    target_type character varying(100) NOT NULL,
    target_key character varying(500) NOT NULL,
    correlation_id character varying(100) NOT NULL,
    safe_details jsonb DEFAULT '{}'::jsonb NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE audit_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_time timestamp with time zone NOT NULL,
    event_version integer DEFAULT 1 NOT NULL,
    actor_type character varying(80) NOT NULL,
    actor_id character varying(500) NOT NULL,
    event_category character varying(100) NOT NULL,
    event_type character varying(160) NOT NULL,
    subject_type character varying(100),
    subject_id character varying(500),
    target_type character varying(100),
    target_id character varying(500),
    target_name_snapshot character varying(500),
    action character varying(100),
    result character varying(40) NOT NULL,
    before_state jsonb,
    after_state jsonb,
    source_ip character varying(128),
    user_agent character varying(1000),
    service_name character varying(160) NOT NULL,
    correlation_id character varying(128) NOT NULL,
    metadata jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: authorization_decision_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE authorization_decision_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    decision_id character varying(100) NOT NULL,
    subject_key character varying(500) NOT NULL,
    resource_key character varying(500) NOT NULL,
    action_key character varying(100) NOT NULL,
    result character varying(20) NOT NULL,
    reason_code character varying(100) NOT NULL,
    model_version character varying(100),
    correlation_id character varying(100) NOT NULL,
    safe_context_hash character varying(128),
    decided_at timestamp with time zone DEFAULT now() NOT NULL,
    normalized_permission character varying(100),
    openfga_allowed boolean,
    policy_allowed boolean,
    latency_ms bigint,
    policy_references jsonb DEFAULT '[]'::jsonb NOT NULL
);

--
-- Name: authorization_grant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE authorization_grant (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    subject_type subject_type NOT NULL,
    subject_id uuid NOT NULL,
    resource_id uuid NOT NULL,
    action_id uuid NOT NULL,
    relation character varying(100) NOT NULL,
    condition_id uuid,
    expires_at timestamp with time zone,
    status lifecycle_status DEFAULT 'ACTIVE'::lifecycle_status NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: condition_definition; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE condition_definition (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    condition_key character varying(160) NOT NULL,
    expression jsonb NOT NULL,
    compiled_version character varying(50) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

--
-- Name: data_policy; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE data_policy (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    policy_key character varying(160) NOT NULL,
    resource_id uuid NOT NULL,
    action_id uuid NOT NULL,
    condition_id uuid,
    obligations jsonb NOT NULL,
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

--
-- Name: directory_group; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE directory_group (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    issuer character varying(255) NOT NULL,
    external_id character varying(255) NOT NULL,
    normalized_path character varying(1024) NOT NULL,
    display_name character varying(255) NOT NULL,
    parent_id uuid,
    status lifecycle_status DEFAULT 'ACTIVE'::lifecycle_status NOT NULL,
    sync_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL
);

--
-- Name: directory_ou; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE directory_ou (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    issuer character varying(255) NOT NULL,
    external_id character varying(512) NOT NULL,
    external_dn character varying(2048) NOT NULL,
    external_path character varying(2048) NOT NULL,
    name character varying(255) NOT NULL,
    parent_ou_id uuid,
    source directory_source DEFAULT 'ACTIVE_DIRECTORY'::directory_source NOT NULL,
    active boolean DEFAULT true NOT NULL,
    directory_managed boolean DEFAULT true NOT NULL,
    last_synced_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

--
-- Name: directory_sync_run; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE directory_sync_run (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    source directory_source NOT NULL,
    status character varying(32) NOT NULL,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    discovered_ous integer DEFAULT 0 NOT NULL,
    discovered_users integer DEFAULT 0 NOT NULL,
    safe_error character varying(1000)
);

--
-- Name: effective_group_membership; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE effective_group_membership (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    access_group_id uuid NOT NULL,
    source_type membership_source_type NOT NULL,
    source_id uuid NOT NULL,
    active boolean DEFAULT true NOT NULL,
    calculated_at timestamp with time zone DEFAULT now() NOT NULL,
    removed_at timestamp with time zone,
    membership_version bigint DEFAULT 1 NOT NULL
);

--
-- Name: external_identity; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE external_identity (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    identity_provider_id uuid,
    issuer character varying(2048) NOT NULL,
    subject character varying(512) NOT NULL,
    last_login_at timestamp with time zone,
    created_by character varying(500) DEFAULT 'migration'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: group_role_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE group_role_assignment (
    group_id uuid NOT NULL,
    role_id uuid NOT NULL,
    expires_at timestamp with time zone,
    assigned_by character varying(500) DEFAULT 'migration'::character varying NOT NULL,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

--
-- Name: identity_provider; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE identity_provider (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(80) NOT NULL,
    name character varying(255) NOT NULL,
    provider_type character varying(40) NOT NULL,
    issuer_url character varying(2048) NOT NULL,
    authorization_endpoint character varying(2048) NOT NULL,
    token_endpoint character varying(2048) NOT NULL,
    jwks_uri character varying(2048) NOT NULL,
    user_info_endpoint character varying(2048),
    client_id character varying(255) NOT NULL,
    client_secret_reference character varying(512) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    tenant_id character varying(160),
    domains character varying(255)[] DEFAULT '{}'::character varying[] NOT NULL,
    scopes character varying(160)[] DEFAULT ARRAY['openid'::character varying(160), 'profile'::character varying(160), 'email'::character varying(160)] NOT NULL,
    audiences character varying(255)[] DEFAULT '{}'::character varying[] NOT NULL,
    subject_claim character varying(160) DEFAULT 'sub'::character varying NOT NULL,
    username_claim character varying(160) DEFAULT 'preferred_username'::character varying NOT NULL,
    groups_claim character varying(160) DEFAULT 'groups'::character varying NOT NULL,
    connection_status character varying(40) DEFAULT 'UNKNOWN'::character varying NOT NULL,
    last_health_check_at timestamp with time zone,
    last_health_error character varying(500),
    version bigint DEFAULT 0 NOT NULL,
    created_by character varying(500) NOT NULL,
    updated_by character varying(500) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT identity_provider_code_format CHECK (((code)::text ~ '^[a-z][a-z0-9-]{2,79}$'::text)),
    CONSTRAINT identity_provider_status_check CHECK (((connection_status)::text = ANY ((ARRAY['UNKNOWN'::character varying, 'ACTIVE'::character varying, 'UNREACHABLE'::character varying, 'INVALID'::character varying])::text[]))),
    CONSTRAINT identity_provider_type_check CHECK (((provider_type)::text = ANY ((ARRAY['OIDC'::character varying, 'KEYCLOAK'::character varying, 'AZURE_AD'::character varying, 'OKTA'::character varying, 'AUTH0'::character varying, 'GOOGLE_WORKSPACE'::character varying])::text[])))
);

--
-- Name: ou_recalculation_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ou_recalculation_job (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    access_group_id uuid NOT NULL,
    status recalculation_job_status DEFAULT 'PENDING'::recalculation_job_status NOT NULL,
    last_user_id uuid,
    processed_users bigint DEFAULT 0 NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    available_at timestamp with time zone DEFAULT now() NOT NULL,
    claimed_at timestamp with time zone,
    claim_owner uuid,
    safe_error character varying(1000),
    requested_by character varying(500) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone
);

--
-- Name: outbound_auth_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE outbound_auth_profile (
    id uuid NOT NULL,
    code character varying(160) NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(1000),
    auth_mode character varying(40) NOT NULL,
    token_connection_ref character varying(255),
    token_endpoint_path character varying(500),
    request_format character varying(40) NOT NULL,
    credential_secret_ref character varying(500),
    scope character varying(500),
    audience character varying(500),
    token_response_pointer character varying(255) DEFAULT '/access_token'::character varying NOT NULL,
    expires_in_response_pointer character varying(255) DEFAULT '/expires_in'::character varying NOT NULL,
    token_type_response_pointer character varying(255) DEFAULT '/token_type'::character varying NOT NULL,
    authorization_scheme character varying(40) DEFAULT 'Bearer'::character varying NOT NULL,
    credential_transport character varying(40) DEFAULT 'USER_AUTHORIZATION_HEADER'::character varying NOT NULL,
    expiry_skew_seconds integer DEFAULT 30 NOT NULL,
    connect_timeout_ms integer DEFAULT 3000 NOT NULL,
    response_timeout_ms integer DEFAULT 10000 NOT NULL,
    max_token_response_size bigint DEFAULT 1048576 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255) NOT NULL,
    CONSTRAINT outbound_auth_profile_auth_mode_check CHECK (((auth_mode)::text = ANY ((ARRAY['FORWARD_USER_TOKEN'::character varying, 'LEGACY_SERVICE_TOKEN'::character varying])::text[]))),
    CONSTRAINT outbound_auth_profile_check CHECK ((((auth_mode)::text = 'FORWARD_USER_TOKEN'::text) OR ((token_connection_ref IS NOT NULL) AND (token_endpoint_path IS NOT NULL) AND (credential_secret_ref IS NOT NULL) AND ((credential_transport)::text = 'INTERNAL_LEGACY_HEADER'::text)))),
    CONSTRAINT outbound_auth_profile_connect_timeout_ms_check CHECK (((connect_timeout_ms >= 100) AND (connect_timeout_ms <= 30000))),
    CONSTRAINT outbound_auth_profile_credential_transport_check CHECK (((credential_transport)::text = ANY ((ARRAY['USER_AUTHORIZATION_HEADER'::character varying, 'INTERNAL_LEGACY_HEADER'::character varying])::text[]))),
    CONSTRAINT outbound_auth_profile_expiry_skew_seconds_check CHECK (((expiry_skew_seconds >= 5) AND (expiry_skew_seconds <= 600))),
    CONSTRAINT outbound_auth_profile_max_token_response_size_check CHECK (((max_token_response_size >= 1024) AND (max_token_response_size <= 5242880))),
    CONSTRAINT outbound_auth_profile_request_format_check CHECK (((request_format)::text = ANY ((ARRAY['FORM_URLENCODED'::character varying, 'JSON'::character varying, 'HTTP_BASIC'::character varying, 'OAUTH_CLIENT_CREDENTIALS'::character varying])::text[]))),
    CONSTRAINT outbound_auth_profile_response_timeout_ms_check CHECK (((response_timeout_ms >= 100) AND (response_timeout_ms <= 120000))),
    CONSTRAINT outbound_auth_profile_token_endpoint_path_check CHECK (((token_endpoint_path IS NULL) OR (((token_endpoint_path)::text ~~ '/%'::text) AND ((token_endpoint_path)::text !~~ '%://%'::text))))
);

--
-- Name: outbound_connection; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE outbound_connection (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    connection_ref character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    kind outbound_connection_kind NOT NULL,
    base_url character varying(1000) NOT NULL,
    tls_required boolean DEFAULT true NOT NULL,
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_by character varying(500) NOT NULL,
    updated_by character varying(500) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT outbound_connection_base_url_check CHECK (((base_url)::text ~ '^https?://[^/?#]+$'::text)),
    CONSTRAINT outbound_connection_check CHECK (((NOT tls_required) OR ((base_url)::text ~~ 'https://%'::text))),
    CONSTRAINT outbound_connection_connection_ref_check CHECK (((connection_ref)::text ~ '^connection://[a-zA-Z0-9._/-]+$'::text))
);

--
-- Name: outbox_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE outbox_event (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    aggregate_type character varying(100) NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type character varying(160) NOT NULL,
    payload jsonb NOT NULL,
    idempotency_key character varying(255) NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    available_at timestamp with time zone DEFAULT now() NOT NULL,
    processed_at timestamp with time zone,
    last_error character varying(1000),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    dead_lettered_at timestamp with time zone,
    sequence bigint NOT NULL,
    claimed_at timestamp with time zone,
    claim_owner uuid
);

--
-- Name: outbox_event_sequence_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE outbox_event_sequence_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: outbox_event_sequence_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE outbox_event_sequence_seq OWNED BY outbox_event.sequence;

--
-- Name: panel; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE panel (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(100) NOT NULL,
    name_fa character varying(255) NOT NULL,
    name_en character varying(255) NOT NULL,
    slug character varying(100) NOT NULL,
    remote_entry_path character varying(500) NOT NULL,
    exposed_module character varying(160) NOT NULL,
    route_base_path character varying(255) NOT NULL,
    icon character varying(100),
    semantic_version character varying(50) NOT NULL,
    contract_version character varying(50) NOT NULL,
    integrity character varying(255),
    health_check_url character varying(500),
    active boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    description character varying(1000),
    service_slug character varying(80) NOT NULL,
    default_route_id character varying(100),
    remote_name character varying(160) NOT NULL,
    active_artifact_id uuid,
    resource_definition_mode character varying(16) DEFAULT 'HYBRID'::character varying NOT NULL,
    classification character varying(8) DEFAULT 'REAL'::character varying NOT NULL,
    resource_manifest_url character varying(1000),
    mf_manifest_url character varying(1000),
    discovery_resource_key character varying(500),
    CONSTRAINT panel_classification_check CHECK (((classification)::text = ANY ((ARRAY['DEMO'::character varying, 'REAL'::character varying])::text[]))),
    CONSTRAINT panel_mf_manifest_url_check CHECK (((mf_manifest_url IS NULL) OR ((mf_manifest_url)::text ~ '^https?://[^/?#]+/.+[.]json$'::text))),
    CONSTRAINT panel_resource_definition_mode_check CHECK (((resource_definition_mode)::text = ANY ((ARRAY['MANIFEST'::character varying, 'MANUAL'::character varying, 'HYBRID'::character varying])::text[]))),
    CONSTRAINT panel_resource_manifest_url_check CHECK (((resource_manifest_url IS NULL) OR ((resource_manifest_url)::text ~ '^https?://[^/?#]+/.+[.]json$'::text))),
    CONSTRAINT panel_route_prefix_format CHECK (((route_base_path)::text ~ '^/[a-z][a-z0-9-]{1,49}$'::text)),
    CONSTRAINT panel_service_slug_format CHECK (((service_slug)::text ~ '^[a-z][a-z0-9-]{1,49}$'::text))
);

--
-- Name: proxy_route; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE proxy_route (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(160) NOT NULL,
    path_prefix character varying(500) NOT NULL,
    service_target_id uuid NOT NULL,
    strip_prefix integer DEFAULT 0 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    panel_id uuid NOT NULL,
    normalized_path_prefix character varying(500) NOT NULL,
    rewrite_pattern character varying(500),
    rewrite_replacement character varying(500),
    priority integer DEFAULT 0 NOT NULL,
    allowed_methods character varying(12)[] DEFAULT ARRAY['GET'::text, 'HEAD'::text, 'OPTIONS'::text, 'POST'::text, 'PUT'::text, 'PATCH'::text, 'DELETE'::text] NOT NULL,
    preserve_host boolean DEFAULT false NOT NULL,
    request_header_policy jsonb DEFAULT '{}'::jsonb NOT NULL,
    response_header_policy jsonb DEFAULT '{}'::jsonb NOT NULL,
    retry_enabled boolean DEFAULT false NOT NULL,
    max_retries integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255) DEFAULT 'migration'::character varying NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255) DEFAULT 'migration'::character varying NOT NULL,
    service_slug character varying(80) NOT NULL,
    outbound_auth_profile_id uuid NOT NULL,
    CONSTRAINT proxy_route_path_prefix_check CHECK (((path_prefix)::text ~~ '/%'::text)),
    CONSTRAINT proxy_route_path_prefix_check1 CHECK (((path_prefix)::text !~~ '%..%'::text)),
    CONSTRAINT proxy_route_prefix_panel CHECK (((normalized_path_prefix)::text ~~ '/%/'::text)),
    CONSTRAINT proxy_route_retry_range CHECK (((max_retries >= 0) AND (max_retries <= 3))),
    CONSTRAINT proxy_route_service_slug_format CHECK (((service_slug)::text ~ '^[a-z][a-z0-9-]{1,49}$'::text))
);

--
-- Name: resource; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE resource (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    resource_key character varying(500) NOT NULL,
    type resource_type NOT NULL,
    parent_id uuid,
    name_fa character varying(255) NOT NULL,
    name_en character varying(255) NOT NULL,
    owner_domain character varying(160),
    classification character varying(80),
    external_system character varying(100),
    external_type character varying(100),
    external_id character varying(255),
    status lifecycle_status DEFAULT 'ACTIVE'::lifecycle_status NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    source character varying(40) DEFAULT 'ADMIN'::character varying NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    panel_id uuid,
    manifest_version character varying(100),
    visibility_enabled boolean DEFAULT true NOT NULL,
    CONSTRAINT resource_source_check CHECK (((source)::text = ANY ((ARRAY['MANIFEST'::character varying, 'ADMIN'::character varying])::text[])))
);

--
-- Name: resource_action; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE resource_action (
    resource_id uuid NOT NULL,
    action_id uuid NOT NULL
);

--
-- Name: resource_api_binding; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE resource_api_binding (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    resource_id uuid NOT NULL,
    action_id uuid NOT NULL,
    http_method character varying(12) NOT NULL,
    path_pattern character varying(500) NOT NULL,
    service_code character varying(160),
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: resource_external_binding; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE resource_external_binding (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    resource_id uuid NOT NULL,
    provider character varying(80) NOT NULL,
    external_type character varying(80) NOT NULL,
    external_id character varying(255) NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: resource_manifest_import; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE resource_manifest_import (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    application_key character varying(500) NOT NULL,
    manifest_version character varying(100) NOT NULL,
    checksum character varying(128) NOT NULL,
    imported_by character varying(255) NOT NULL,
    imported_at timestamp with time zone DEFAULT now() NOT NULL,
    payload jsonb NOT NULL,
    panel_id uuid,
    schema_version character varying(30) DEFAULT '1.0'::character varying NOT NULL,
    source_url character varying(1000),
    workflow_status character varying(20) DEFAULT 'PUBLISHED'::character varying NOT NULL,
    diff_summary jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published_at timestamp with time zone,
    published_by character varying(255),
    rejected_at timestamp with time zone,
    rejected_by character varying(255),
    CONSTRAINT resource_manifest_workflow_status_check CHECK (((workflow_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'REJECTED'::character varying])::text[])))
);

--
-- Name: route_operation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE route_operation (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    proxy_route_id uuid NOT NULL,
    http_method character varying(12) NOT NULL,
    path_pattern character varying(500) NOT NULL,
    resource_id uuid NOT NULL,
    action_id uuid NOT NULL,
    max_body_bytes bigint DEFAULT 1048576 NOT NULL,
    normalized_path_pattern character varying(500) NOT NULL,
    resource_key character varying(500) NOT NULL,
    action_key character varying(100) NOT NULL,
    authorization_required boolean DEFAULT true NOT NULL,
    data_policy_key character varying(160),
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255) DEFAULT 'migration'::character varying NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255) DEFAULT 'migration'::character varying NOT NULL,
    CONSTRAINT route_operation_method_upper CHECK (((http_method)::text = upper((http_method)::text)))
);

--
-- Name: schema_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE schema_version (
    component character varying(100) NOT NULL,
    version character varying(100) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: service_target; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE service_target (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(160) NOT NULL,
    gateway_base_url character varying(500) NOT NULL,
    tls_profile_ref character varying(255),
    active boolean DEFAULT true NOT NULL,
    connect_timeout_ms integer DEFAULT 3000 NOT NULL,
    response_timeout_ms integer DEFAULT 10000 NOT NULL,
    max_response_size bigint DEFAULT 10485760 NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(1000),
    upstream_base_path character varying(500) DEFAULT '/'::character varying NOT NULL,
    environment character varying(80) DEFAULT 'OPERATION'::character varying NOT NULL,
    secret_ref character varying(255),
    health_check_path character varying(500) DEFAULT '/health'::character varying NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255) DEFAULT 'migration'::character varying NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255) DEFAULT 'migration'::character varying NOT NULL,
    outbound_auth_profile_id uuid,
    gateway_connection_ref character varying(255) DEFAULT 'connection://operation-gateway'::character varying NOT NULL
);

--
-- Name: superset_access_sync; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE superset_access_sync (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    grant_id uuid NOT NULL,
    status sync_status DEFAULT 'PENDING'::sync_status NOT NULL,
    idempotency_key character varying(255) NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    last_error character varying(1000),
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: superset_asset; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE superset_asset (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    resource_id uuid NOT NULL,
    external_id character varying(255) NOT NULL,
    asset_type character varying(40) NOT NULL,
    title character varying(500) NOT NULL,
    url_path character varying(1000) NOT NULL,
    owner_external_id character varying(255),
    published boolean DEFAULT false NOT NULL,
    tags jsonb DEFAULT '[]'::jsonb NOT NULL,
    last_synced_version character varying(100),
    synchronized_at timestamp with time zone,
    instance_id uuid NOT NULL
);

--
-- Name: superset_instance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE superset_instance (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(80) NOT NULL,
    name character varying(255) NOT NULL,
    zone superset_zone NOT NULL,
    base_url character varying(1000) NOT NULL,
    connection_ref character varying(255) NOT NULL,
    auth_mode character varying(40) DEFAULT 'REMOTE_USER'::character varying NOT NULL,
    tls_required boolean DEFAULT true NOT NULL,
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_by character varying(500) NOT NULL,
    updated_by character varying(500) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    proxy_mode boolean DEFAULT true NOT NULL,
    health_status character varying(32) DEFAULT 'UNKNOWN'::character varying NOT NULL,
    health_checked_at timestamp with time zone,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT superset_instance_auth_mode_check CHECK (((auth_mode)::text = ANY ((ARRAY['REMOTE_USER'::character varying, 'OIDC'::character varying, 'GUEST_TOKEN'::character varying])::text[]))),
    CONSTRAINT superset_instance_base_url_check CHECK (((base_url)::text ~ '^https?://[^/?#]+(?:/[^?#]*)?$'::text)),
    CONSTRAINT superset_instance_check CHECK (((NOT tls_required) OR ((base_url)::text ~~ 'https://%'::text))),
    CONSTRAINT superset_instance_code_check CHECK (((code)::text ~ '^[a-z][a-z0-9-]{2,79}$'::text)),
    CONSTRAINT superset_instance_connection_ref_check CHECK (((connection_ref)::text ~ '^connection://[a-zA-Z0-9._/-]+$'::text)),
    CONSTRAINT superset_instance_health_status_check CHECK (((health_status)::text = ANY ((ARRAY['UNKNOWN'::character varying, 'ACTIVE'::character varying, 'UNREACHABLE'::character varying, 'DISABLED'::character varying])::text[])))
);

--
-- Name: superset_proxy_mapping; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE superset_proxy_mapping (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    public_instance_id uuid NOT NULL,
    operation_instance_id uuid NOT NULL,
    public_path character varying(255) NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_by character varying(500) NOT NULL,
    updated_by character varying(500) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT superset_proxy_mapping_public_path_check CHECK (((public_path)::text ~ '^/[a-zA-Z0-9/_-]*$'::text))
);

--
-- Name: superset_subject_mapping; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE superset_subject_mapping (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    subject_type subject_type NOT NULL,
    subject_id uuid NOT NULL,
    superset_subject_type character varying(40) NOT NULL,
    superset_subject_id character varying(255) NOT NULL
);

--
-- Name: ui_menu_override; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ui_menu_override (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    panel_id uuid NOT NULL,
    menu_id character varying(100) NOT NULL,
    title character varying(255),
    icon character varying(100),
    sort_order integer,
    hidden boolean DEFAULT false NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(255) DEFAULT 'migration'::character varying NOT NULL,
    source character varying(16) DEFAULT 'MANIFEST'::character varying NOT NULL,
    node_type character varying(20),
    parent_key character varying(100),
    page_key character varying(160),
    external_url character varying(1000),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT ui_navigation_external_url_check CHECK (((external_url IS NULL) OR ((external_url)::text ~ '^https?://'::text))),
    CONSTRAINT ui_navigation_override_source_check CHECK (((source)::text = ANY ((ARRAY['MANIFEST'::character varying, 'ADMIN'::character varying])::text[]))),
    CONSTRAINT ui_navigation_override_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DEPRECATED'::character varying])::text[]))),
    CONSTRAINT ui_navigation_override_type_check CHECK (((node_type)::text = ANY ((ARRAY['GROUP'::character varying, 'PAGE'::character varying, 'EXTERNAL_LINK'::character varying])::text[])))
);

--
-- Name: ui_module_artifact; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ui_module_artifact (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    panel_id uuid NOT NULL,
    artifact_version character varying(50) NOT NULL,
    remote_entry_url character varying(1000) NOT NULL,
    remote_name character varying(160) NOT NULL,
    exposed_module character varying(160) DEFAULT './plugin'::character varying NOT NULL,
    contract_version character varying(50) NOT NULL,
    schema_version character varying(30) DEFAULT '1.0'::character varying NOT NULL,
    integrity character varying(255),
    manifest_snapshot jsonb NOT NULL,
    validation_status character varying(20) NOT NULL,
    validation_error character varying(1000),
    immutable boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(255) DEFAULT 'migration'::character varying NOT NULL,
    manifest_checksum character varying(64) NOT NULL,
    source_url character varying(1000),
    synchronized_at timestamp with time zone,
    CONSTRAINT ui_artifact_exposed_module_format CHECK (((exposed_module)::text ~ '^[.]/[A-Za-z][A-Za-z0-9_./-]*$'::text)),
    CONSTRAINT ui_artifact_integrity_format CHECK (((integrity IS NULL) OR ((integrity)::text ~ '^sha(256|384|512)-[A-Za-z0-9+/]+={0,2}$'::text))),
    CONSTRAINT ui_artifact_remote_entry_format CHECK (((remote_entry_url)::text ~ '^https?://[^/?#]+/.+[.]js$'::text)),
    CONSTRAINT ui_module_artifact_validation_status_check CHECK (((validation_status)::text = ANY ((ARRAY['VALID'::character varying, 'INVALID'::character varying, 'PENDING'::character varying])::text[])))
);

--
-- Name: user_group_membership; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_group_membership (
    user_id uuid NOT NULL,
    group_id uuid NOT NULL,
    synchronized_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: user_ou_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_ou_assignment (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    ou_id uuid NOT NULL,
    source directory_source DEFAULT 'ACTIVE_DIRECTORY'::directory_source NOT NULL,
    active boolean DEFAULT true NOT NULL,
    first_seen_at timestamp with time zone DEFAULT now() NOT NULL,
    last_seen_at timestamp with time zone DEFAULT now() NOT NULL,
    removed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: user_role_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE user_role_assignment (
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    expires_at timestamp with time zone,
    assigned_by character varying(500) DEFAULT 'migration'::character varying NOT NULL,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);

--
-- Name: outbox_event sequence; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY outbox_event ALTER COLUMN sequence SET DEFAULT nextval('outbox_event_sequence_seq'::regclass);

--
-- Data for Name: access_group; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: access_group_ou_rule; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: access_group_role_assignment; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: action; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO action VALUES ('5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c', 'view', 'مشاهده', 'View');
INSERT INTO action VALUES ('61d2d42f-8e56-47b1-b2bc-f7d2af9ad88b', 'create', 'ایجاد', 'Create');
INSERT INTO action VALUES ('d5cba6cd-30ee-4ca5-901b-d3730d8011c1', 'update', 'ویرایش', 'Update');
INSERT INTO action VALUES ('adcfa2b3-5294-46b0-bfc5-0a021cc50e74', 'approve', 'تأیید', 'Approve');
INSERT INTO action VALUES ('d079873c-fa53-41d4-b358-18f0e3b8f8b8', 'admin', 'مدیریت', 'Admin');
INSERT INTO action VALUES ('6c5e41ac-0d75-40b3-b37d-1cd00efbe137', 'list', 'فهرست', 'List');
INSERT INTO action VALUES ('6d8985bd-14ba-4f0c-846e-ae4e8fd8686f', 'reject', 'رد', 'Reject');
INSERT INTO action VALUES ('b7a0cab3-ebf1-4199-8b2e-e2351309236d', 'delete', 'حذف', 'Delete');
INSERT INTO action VALUES ('78a16a8d-4dd0-4a3b-9721-af3043097e37', 'share', 'اشتراک‌گذاری', 'Share');
INSERT INTO action VALUES ('d5d2dfe9-9e6d-477c-b9c7-05f0fc9e4ef7', 'export', 'خروجی', 'Export');
INSERT INTO action VALUES ('d659d18a-eb5e-4746-ac42-9ce3870e5a65', 'manage', 'مدیریت کامل', 'Manage');
INSERT INTO action VALUES ('77b0001e-07af-4158-bea4-ae6e89a82f20', 'view_api', 'مشاهده لاگ API', 'View API logs');
INSERT INTO action VALUES ('1de27a98-9066-4649-bf04-d91fffba57ca', 'view_audit', 'مشاهده لاگ ممیزی', 'View audit logs');
INSERT INTO action VALUES ('d3b5ebfc-366d-4dd8-92bb-7848d9669daa', 'view_errors', 'مشاهده خطاها', 'View error logs');
INSERT INTO action VALUES ('e236979b-d46a-4b7e-92e8-ab17d10d5ae4', 'activate', 'فعال‌سازی', 'Activate');
INSERT INTO action VALUES ('2c69092e-71a7-46fa-b96f-e831b5886271', 'test', 'آزمایش', 'Test');
INSERT INTO action VALUES ('9a009d26-12fd-4450-9ba2-3bc541b6f7c4', 'invalidate-token', 'ابطال توکن', 'Invalidate token');
INSERT INTO action VALUES ('017d401e-7f51-4992-92bc-701c78718538', 'update-credential-reference', 'تغییر مرجع اعتبارنامه', 'Update credential reference');
INSERT INTO action VALUES ('80cd4bdd-692b-4b1c-a518-d2d21a8d4383', 'access', 'دسترسی', 'Access');
INSERT INTO action VALUES ('48303ac1-44eb-4387-bedb-b90db3ea3ad9', 'download', 'دریافت', 'Download');
INSERT INTO action VALUES ('332944e3-d0ec-4d66-a7b9-1bbdf0f20c05', 'execute', 'اجرا', 'Execute');
INSERT INTO action VALUES ('ecb3d919-1641-499c-a988-8ced6ce570e4', 'import', 'ورود اطلاعات', 'Import');
INSERT INTO action VALUES ('97fedf3b-2441-45ba-920b-f50c7d6614d7', 'upload', 'بارگذاری', 'Upload');
INSERT INTO action VALUES ('8f26214f-59a0-4150-ba9f-aaf34a1cd5dc', 'assign', 'انتصاب', 'Assign');

--
-- Data for Name: api_log; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: app_user; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: application_group_grant; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: application_role; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO application_role VALUES ('5b3d0a86-14b2-4925-8af4-019e4c394695', 'aurevia-administrator', 'مدیر سامانه', 'Aurevia Administrator', 'ACTIVE', 0, '2026-09-22 02:27:12.613132+00', '2026-09-22 02:27:12.613132+00');
INSERT INTO application_role VALUES ('8560d5e7-a705-4825-8392-ef3757fb367b', 'superset-designer', 'طراح و راهبر گزارش', 'Superset report designer', 'ACTIVE', 0, '2026-09-22 02:27:13.850456+00', '2026-09-22 02:27:13.850456+00');
INSERT INTO application_role VALUES ('bcd3486e-b724-45b9-a5a1-46af5271949a', 'superset-viewer', 'مشاهده‌گر گزارش', 'Superset report viewer', 'ACTIVE', 0, '2026-09-22 02:27:13.850456+00', '2026-09-22 02:27:13.850456+00');

--
-- Data for Name: audit_event; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: audit_log; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: authorization_decision_log; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: authorization_grant; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO authorization_grant VALUES ('ae01bd2f-3762-40aa-aada-0541fde6e16c', 'ROLE', '8560d5e7-a705-4825-8392-ef3757fb367b', 'a39751ac-938e-4caa-96c3-166e8e829651', '5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c', 'viewer', NULL, NULL, 'ACTIVE', 0, '2026-09-22 02:27:13.850456+00');
INSERT INTO authorization_grant VALUES ('8529074d-c06f-4e70-91e2-db08c87e4421', 'ROLE', '8560d5e7-a705-4825-8392-ef3757fb367b', 'deb47e0e-d4a0-4165-a453-1834c32957bd', '5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c', 'viewer', NULL, NULL, 'ACTIVE', 0, '2026-09-22 02:27:13.850456+00');
INSERT INTO authorization_grant VALUES ('49d9d852-cc64-4636-b3ed-70d3cfc6725c', 'ROLE', '8560d5e7-a705-4825-8392-ef3757fb367b', '69a132ef-70bd-4d5c-b7fc-ed562aeb8f4b', 'd079873c-fa53-41d4-b358-18f0e3b8f8b8', 'manager', NULL, NULL, 'ACTIVE', 0, '2026-09-22 02:27:13.850456+00');
INSERT INTO authorization_grant VALUES ('ada83775-6db7-4f33-94c7-a6a32c00b56d', 'ROLE', '8560d5e7-a705-4825-8392-ef3757fb367b', '029004b0-e3d9-4f75-a205-f1f73eddb65f', '5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c', 'viewer', NULL, NULL, 'ACTIVE', 0, '2026-09-22 02:27:14.848153+00');
INSERT INTO authorization_grant VALUES ('9abdaa26-7333-4845-806a-a593cbda1f3d', 'ROLE', '8560d5e7-a705-4825-8392-ef3757fb367b', '029004b0-e3d9-4f75-a205-f1f73eddb65f', 'd5cba6cd-30ee-4ca5-901b-d3730d8011c1', 'editor', NULL, NULL, 'ACTIVE', 0, '2026-09-22 02:27:14.848153+00');
INSERT INTO authorization_grant VALUES ('b8bbbee0-9057-4136-a67f-b453fd121184', 'ROLE', 'bcd3486e-b724-45b9-a5a1-46af5271949a', 'deb47e0e-d4a0-4165-a453-1834c32957bd', '5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c', 'viewer', NULL, NULL, 'ARCHIVED', 1, '2026-09-22 02:27:13.850456+00');

--
-- Data for Name: condition_definition; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: data_policy; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: directory_group; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: directory_ou; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: directory_sync_run; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: effective_group_membership; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: external_identity; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: group_role_assignment; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: identity_provider; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: ou_recalculation_job; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: outbound_auth_profile; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO outbound_auth_profile VALUES ('1dc7cc3c-5cb9-466d-88ef-0bcb04d8af3d', 'public-iam-forward', 'Public IAM user token', 'Forward current Public IAM token unchanged', 'FORWARD_USER_TOKEN', NULL, NULL, 'FORM_URLENCODED', NULL, NULL, NULL, '/access_token', '/expires_in', '/token_type', 'Bearer', 'USER_AUTHORIZATION_HEADER', 30, 3000, 10000, 1048576, true, 0, '2026-09-22 02:27:13.124942+00', 'migration', '2026-09-22 02:27:13.124942+00', 'migration');

--
-- Data for Name: outbound_connection; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: outbox_event; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO outbox_event VALUES ('d10be760-974d-4f0f-9bbb-176c22526344', 'resource', 'deb47e0e-d4a0-4165-a453-1834c32957bd', 'RESOURCE_PARENT_WRITE', '{"user": "application:aurevia", "object": "application:aurevia/reports", "relation": "parent"}', 'baseline:parent:deb47e0e-d4a0-4165-a453-1834c32957bd', 0, '2026-09-22 02:27:15.496941+00', NULL, NULL, '2026-09-22 02:27:15.496941+00', NULL, 635, NULL, NULL);
INSERT INTO outbox_event VALUES ('d4ae7c26-1f18-494d-b0df-cb219ba539dd', 'resource', '029004b0-e3d9-4f75-a205-f1f73eddb65f', 'RESOURCE_PARENT_WRITE', '{"user": "application:aurevia", "object": "external_resource:superset-public", "relation": "parent"}', 'baseline:parent:029004b0-e3d9-4f75-a205-f1f73eddb65f', 0, '2026-09-22 02:27:15.496941+00', NULL, NULL, '2026-09-22 02:27:15.496941+00', NULL, 636, NULL, NULL);
INSERT INTO outbox_event VALUES ('113f6282-03b4-4427-be80-567dfde54065', 'resource', 'a39751ac-938e-4caa-96c3-166e8e829651', 'RESOURCE_PARENT_WRITE', '{"user": "application:aurevia", "object": "application:aurevia/admin", "relation": "parent"}', 'baseline:parent:a39751ac-938e-4caa-96c3-166e8e829651', 0, '2026-09-22 02:27:15.496941+00', NULL, NULL, '2026-09-22 02:27:15.496941+00', NULL, 637, NULL, NULL);
INSERT INTO outbox_event VALUES ('815357c0-0aaa-4fb3-86c0-76291a1ce221', 'resource', '69a132ef-70bd-4d5c-b7fc-ed562aeb8f4b', 'RESOURCE_PARENT_WRITE', '{"user": "application:aurevia/admin", "object": "resource:module/admin.superset-catalog", "relation": "parent"}', 'baseline:parent:69a132ef-70bd-4d5c-b7fc-ed562aeb8f4b', 0, '2026-09-22 02:27:15.496941+00', NULL, NULL, '2026-09-22 02:27:15.496941+00', NULL, 638, NULL, NULL);
INSERT INTO outbox_event VALUES ('31c9a5dc-0ecb-471e-b418-e5d11552f2ed', 'resource', 'a6b5e8ee-a306-4907-9432-9b4a3f219a6c', 'RESOURCE_PARENT_WRITE', '{"user": "application:aurevia/admin", "object": "resource:integration.auth-profile", "relation": "parent"}', 'baseline:parent:a6b5e8ee-a306-4907-9432-9b4a3f219a6c', 0, '2026-09-22 02:27:15.496941+00', NULL, NULL, '2026-09-22 02:27:15.496941+00', NULL, 639, NULL, NULL);
INSERT INTO outbox_event VALUES ('03f432ce-12fa-4d04-bc8d-e83d0050b42b', 'resource', 'c354e100-3870-4bf6-b89c-e5ec33581f1a', 'RESOURCE_PARENT_WRITE', '{"user": "application:aurevia/admin", "object": "resource:proxy.operation", "relation": "parent"}', 'baseline:parent:c354e100-3870-4bf6-b89c-e5ec33581f1a', 0, '2026-09-22 02:27:15.496941+00', NULL, NULL, '2026-09-22 02:27:15.496941+00', NULL, 640, NULL, NULL);
INSERT INTO outbox_event VALUES ('b3f3dfcb-4c28-4e9c-a4f6-e6b84c17fb31', 'resource', '5504d6a4-e62d-460a-b87b-5e8e63411789', 'RESOURCE_PARENT_WRITE', '{"user": "application:aurevia/admin", "object": "resource:proxy.route", "relation": "parent"}', 'baseline:parent:5504d6a4-e62d-460a-b87b-5e8e63411789', 0, '2026-09-22 02:27:15.496941+00', NULL, NULL, '2026-09-22 02:27:15.496941+00', NULL, 641, NULL, NULL);
INSERT INTO outbox_event VALUES ('07578409-24cd-4e0f-8a7f-cd39e62c15b5', 'resource', '97c6fb2f-a814-40f4-91cd-c6cfc157b844', 'RESOURCE_PARENT_WRITE', '{"user": "application:aurevia/admin", "object": "resource:proxy.target", "relation": "parent"}', 'baseline:parent:97c6fb2f-a814-40f4-91cd-c6cfc157b844', 0, '2026-09-22 02:27:15.496941+00', NULL, NULL, '2026-09-22 02:27:15.496941+00', NULL, 642, NULL, NULL);
INSERT INTO outbox_event VALUES ('484cb491-8e24-47de-add2-35e424928fb9', 'grant', 'ae01bd2f-3762-40aa-aada-0541fde6e16c', 'GRANT_WRITE', '{"user": "role:superset-designer#assignee", "object": "application:aurevia/admin", "relation": "viewer"}', 'baseline:grant:ae01bd2f-3762-40aa-aada-0541fde6e16c', 0, '2026-09-22 02:27:15.496941+00', NULL, NULL, '2026-09-22 02:27:15.496941+00', NULL, 643, NULL, NULL);
INSERT INTO outbox_event VALUES ('de8db38a-8d09-46d6-bc6a-1132a9d4b402', 'grant', '9abdaa26-7333-4845-806a-a593cbda1f3d', 'GRANT_WRITE', '{"user": "role:superset-designer#assignee", "object": "external_resource:superset-public", "relation": "editor"}', 'baseline:grant:9abdaa26-7333-4845-806a-a593cbda1f3d', 0, '2026-09-22 02:27:15.496941+00', NULL, NULL, '2026-09-22 02:27:15.496941+00', NULL, 644, NULL, NULL);
INSERT INTO outbox_event VALUES ('0bb064f3-5163-4286-9752-9d4b6d2d90c4', 'grant', 'ada83775-6db7-4f33-94c7-a6a32c00b56d', 'GRANT_WRITE', '{"user": "role:superset-designer#assignee", "object": "external_resource:superset-public", "relation": "viewer"}', 'baseline:grant:ada83775-6db7-4f33-94c7-a6a32c00b56d', 0, '2026-09-22 02:27:15.496941+00', NULL, NULL, '2026-09-22 02:27:15.496941+00', NULL, 645, NULL, NULL);
INSERT INTO outbox_event VALUES ('ffffd6a1-6eba-4e51-8f09-27fd949a1404', 'grant', '8529074d-c06f-4e70-91e2-db08c87e4421', 'GRANT_WRITE', '{"user": "role:superset-designer#assignee", "object": "application:aurevia/reports", "relation": "viewer"}', 'baseline:grant:8529074d-c06f-4e70-91e2-db08c87e4421', 0, '2026-09-22 02:27:15.496941+00', NULL, NULL, '2026-09-22 02:27:15.496941+00', NULL, 646, NULL, NULL);
INSERT INTO outbox_event VALUES ('21d7de7c-dc54-4ee9-9032-d0434da2e955', 'grant', '49d9d852-cc64-4636-b3ed-70d3cfc6725c', 'GRANT_WRITE', '{"user": "role:superset-designer#assignee", "object": "resource:module/admin.superset-catalog", "relation": "manager"}', 'baseline:grant:49d9d852-cc64-4636-b3ed-70d3cfc6725c', 0, '2026-09-22 02:27:15.496941+00', NULL, NULL, '2026-09-22 02:27:15.496941+00', NULL, 647, NULL, NULL);

--
-- Data for Name: panel; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO panel VALUES ('84a0d6bd-2377-4c88-b9b2-138622a388e2', 'ADMIN', 'مدیریت', 'Administration', 'admin', 'http://localhost:3001/remoteEntry.js', './bootstrap', '/admin', NULL, '0.6.0', '1.0', NULL, NULL, true, 10, 5, '2026-09-22 02:27:12.461827+00', '2026-09-22 02:27:14.994506+00', NULL, 'admin', 'operator-guide', 'aurevia_admin', 'de76f4b6-7176-4e3c-b129-3d7928308218', 'HYBRID', 'DEMO', 'http://localhost:3001/resource-manifest.json', 'http://localhost:3001/mf-manifest.json', NULL);

--
-- Data for Name: proxy_route; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: resource; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO resource VALUES ('0adab250-00da-42d7-9cc4-ff288e0a6c19', 'application:aurevia', 'APPLICATION', NULL, 'اوراویا', 'Aurevia', 'platform', NULL, NULL, NULL, NULL, 'ACTIVE', 0, '2026-09-22 02:27:12.461827+00', '2026-09-22 02:27:12.461827+00', 'ADMIN', '{}', NULL, NULL, true);
INSERT INTO resource VALUES ('5a809aeb-4ff6-4d69-ae82-f262bd7d7300', 'business_resource:public-zone-logs', 'BUSINESS_RESOURCE', NULL, 'لاگ‌های ناحیه عمومی', 'Public zone logs', 'platform', 'CONFIDENTIAL', NULL, NULL, NULL, 'ACTIVE', 0, '2026-09-22 02:27:12.971513+00', '2026-09-22 02:27:12.971513+00', 'ADMIN', '{}', NULL, NULL, true);
INSERT INTO resource VALUES ('97c6fb2f-a814-40f4-91cd-c6cfc157b844', 'proxy.target', 'API_RESOURCE', 'a39751ac-938e-4caa-96c3-166e8e829651', 'مقصدهای پراکسی', 'Proxy targets', 'platform', NULL, NULL, NULL, NULL, 'ACTIVE', 0, '2026-09-22 02:27:13.042046+00', '2026-09-22 02:27:13.042046+00', 'ADMIN', '{}', NULL, NULL, true);
INSERT INTO resource VALUES ('5504d6a4-e62d-460a-b87b-5e8e63411789', 'proxy.route', 'API_RESOURCE', 'a39751ac-938e-4caa-96c3-166e8e829651', 'مسیرهای پراکسی', 'Proxy routes', 'platform', NULL, NULL, NULL, NULL, 'ACTIVE', 0, '2026-09-22 02:27:13.042046+00', '2026-09-22 02:27:13.042046+00', 'ADMIN', '{}', NULL, NULL, true);
INSERT INTO resource VALUES ('c354e100-3870-4bf6-b89c-e5ec33581f1a', 'proxy.operation', 'API_RESOURCE', 'a39751ac-938e-4caa-96c3-166e8e829651', 'عملیات مسیر', 'Route operations', 'platform', NULL, NULL, NULL, NULL, 'ACTIVE', 0, '2026-09-22 02:27:13.042046+00', '2026-09-22 02:27:13.042046+00', 'ADMIN', '{}', NULL, NULL, true);
INSERT INTO resource VALUES ('a6b5e8ee-a306-4907-9432-9b4a3f219a6c', 'integration.auth-profile', 'API_RESOURCE', 'a39751ac-938e-4caa-96c3-166e8e829651', 'پروفایل‌های احراز هویت سرویس‌ها', 'Outbound authentication profiles', 'platform', NULL, NULL, NULL, NULL, 'ACTIVE', 0, '2026-09-22 02:27:13.124942+00', '2026-09-22 02:27:13.124942+00', 'ADMIN', '{}', NULL, NULL, true);
INSERT INTO resource VALUES ('a39751ac-938e-4caa-96c3-166e8e829651', 'application:aurevia/admin', 'APPLICATION', '0adab250-00da-42d7-9cc4-ff288e0a6c19', 'پنل مدیریت', 'Administration panel', 'platform', NULL, NULL, NULL, NULL, 'ACTIVE', 0, '2026-09-22 02:27:12.690856+00', '2026-09-22 02:27:12.690856+00', 'MANIFEST', '{}', '84a0d6bd-2377-4c88-b9b2-138622a388e2', NULL, true);
INSERT INTO resource VALUES ('029004b0-e3d9-4f75-a205-f1f73eddb65f', 'external_resource:superset-public', 'EXTERNAL_RESOURCE', '0adab250-00da-42d7-9cc4-ff288e0a6c19', 'سوپرست', 'Superset', 'reports', NULL, NULL, NULL, NULL, 'ACTIVE', 1, '2026-09-22 02:27:12.461827+00', '2026-09-22 02:27:14.518057+00', 'MANIFEST', '{"provider": "SUPERSET", "logicalIntegration": true}', NULL, NULL, true);
INSERT INTO resource VALUES ('deb47e0e-d4a0-4165-a453-1834c32957bd', 'application:aurevia/reports', 'APPLICATION', '0adab250-00da-42d7-9cc4-ff288e0a6c19', 'پنل گزارش‌ها', 'Reports panel', 'reports', NULL, NULL, NULL, NULL, 'ACTIVE', 0, '2026-09-22 02:27:12.690856+00', '2026-09-22 02:27:12.690856+00', 'MANIFEST', '{}', NULL, NULL, true);
INSERT INTO resource VALUES ('69a132ef-70bd-4d5c-b7fc-ed562aeb8f4b', 'module:admin.superset-catalog', 'MODULE', 'a39751ac-938e-4caa-96c3-166e8e829651', 'راهبری کاتالوگ گزارش‌ها', 'Superset catalog administration', 'reports', 'RESTRICTED', NULL, NULL, NULL, 'ACTIVE', 0, '2026-09-22 02:27:13.850456+00', '2026-09-22 02:27:13.850456+00', 'MANIFEST', '{}', NULL, NULL, true);

--
-- Data for Name: resource_action; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO resource_action VALUES ('0adab250-00da-42d7-9cc4-ff288e0a6c19', '5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c');
INSERT INTO resource_action VALUES ('029004b0-e3d9-4f75-a205-f1f73eddb65f', '5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c');
INSERT INTO resource_action VALUES ('0adab250-00da-42d7-9cc4-ff288e0a6c19', 'd079873c-fa53-41d4-b358-18f0e3b8f8b8');
INSERT INTO resource_action VALUES ('a39751ac-938e-4caa-96c3-166e8e829651', '5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c');
INSERT INTO resource_action VALUES ('a39751ac-938e-4caa-96c3-166e8e829651', 'd079873c-fa53-41d4-b358-18f0e3b8f8b8');
INSERT INTO resource_action VALUES ('deb47e0e-d4a0-4165-a453-1834c32957bd', '5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c');
INSERT INTO resource_action VALUES ('deb47e0e-d4a0-4165-a453-1834c32957bd', 'd079873c-fa53-41d4-b358-18f0e3b8f8b8');
INSERT INTO resource_action VALUES ('5a809aeb-4ff6-4d69-ae82-f262bd7d7300', '5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c');
INSERT INTO resource_action VALUES ('5a809aeb-4ff6-4d69-ae82-f262bd7d7300', 'd5d2dfe9-9e6d-477c-b9c7-05f0fc9e4ef7');
INSERT INTO resource_action VALUES ('5a809aeb-4ff6-4d69-ae82-f262bd7d7300', '77b0001e-07af-4158-bea4-ae6e89a82f20');
INSERT INTO resource_action VALUES ('5a809aeb-4ff6-4d69-ae82-f262bd7d7300', '1de27a98-9066-4649-bf04-d91fffba57ca');
INSERT INTO resource_action VALUES ('5a809aeb-4ff6-4d69-ae82-f262bd7d7300', 'd3b5ebfc-366d-4dd8-92bb-7848d9669daa');
INSERT INTO resource_action VALUES ('97c6fb2f-a814-40f4-91cd-c6cfc157b844', '5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c');
INSERT INTO resource_action VALUES ('5504d6a4-e62d-460a-b87b-5e8e63411789', '5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c');
INSERT INTO resource_action VALUES ('c354e100-3870-4bf6-b89c-e5ec33581f1a', '5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c');
INSERT INTO resource_action VALUES ('97c6fb2f-a814-40f4-91cd-c6cfc157b844', '61d2d42f-8e56-47b1-b2bc-f7d2af9ad88b');
INSERT INTO resource_action VALUES ('5504d6a4-e62d-460a-b87b-5e8e63411789', '61d2d42f-8e56-47b1-b2bc-f7d2af9ad88b');
INSERT INTO resource_action VALUES ('c354e100-3870-4bf6-b89c-e5ec33581f1a', '61d2d42f-8e56-47b1-b2bc-f7d2af9ad88b');
INSERT INTO resource_action VALUES ('97c6fb2f-a814-40f4-91cd-c6cfc157b844', 'd5cba6cd-30ee-4ca5-901b-d3730d8011c1');
INSERT INTO resource_action VALUES ('5504d6a4-e62d-460a-b87b-5e8e63411789', 'd5cba6cd-30ee-4ca5-901b-d3730d8011c1');
INSERT INTO resource_action VALUES ('c354e100-3870-4bf6-b89c-e5ec33581f1a', 'd5cba6cd-30ee-4ca5-901b-d3730d8011c1');
INSERT INTO resource_action VALUES ('97c6fb2f-a814-40f4-91cd-c6cfc157b844', 'd079873c-fa53-41d4-b358-18f0e3b8f8b8');
INSERT INTO resource_action VALUES ('5504d6a4-e62d-460a-b87b-5e8e63411789', 'd079873c-fa53-41d4-b358-18f0e3b8f8b8');
INSERT INTO resource_action VALUES ('c354e100-3870-4bf6-b89c-e5ec33581f1a', 'd079873c-fa53-41d4-b358-18f0e3b8f8b8');
INSERT INTO resource_action VALUES ('97c6fb2f-a814-40f4-91cd-c6cfc157b844', '6c5e41ac-0d75-40b3-b37d-1cd00efbe137');
INSERT INTO resource_action VALUES ('5504d6a4-e62d-460a-b87b-5e8e63411789', '6c5e41ac-0d75-40b3-b37d-1cd00efbe137');
INSERT INTO resource_action VALUES ('c354e100-3870-4bf6-b89c-e5ec33581f1a', '6c5e41ac-0d75-40b3-b37d-1cd00efbe137');
INSERT INTO resource_action VALUES ('97c6fb2f-a814-40f4-91cd-c6cfc157b844', 'b7a0cab3-ebf1-4199-8b2e-e2351309236d');
INSERT INTO resource_action VALUES ('5504d6a4-e62d-460a-b87b-5e8e63411789', 'b7a0cab3-ebf1-4199-8b2e-e2351309236d');
INSERT INTO resource_action VALUES ('c354e100-3870-4bf6-b89c-e5ec33581f1a', 'b7a0cab3-ebf1-4199-8b2e-e2351309236d');
INSERT INTO resource_action VALUES ('97c6fb2f-a814-40f4-91cd-c6cfc157b844', 'e236979b-d46a-4b7e-92e8-ab17d10d5ae4');
INSERT INTO resource_action VALUES ('5504d6a4-e62d-460a-b87b-5e8e63411789', 'e236979b-d46a-4b7e-92e8-ab17d10d5ae4');
INSERT INTO resource_action VALUES ('c354e100-3870-4bf6-b89c-e5ec33581f1a', 'e236979b-d46a-4b7e-92e8-ab17d10d5ae4');
INSERT INTO resource_action VALUES ('97c6fb2f-a814-40f4-91cd-c6cfc157b844', '2c69092e-71a7-46fa-b96f-e831b5886271');
INSERT INTO resource_action VALUES ('5504d6a4-e62d-460a-b87b-5e8e63411789', '2c69092e-71a7-46fa-b96f-e831b5886271');
INSERT INTO resource_action VALUES ('c354e100-3870-4bf6-b89c-e5ec33581f1a', '2c69092e-71a7-46fa-b96f-e831b5886271');
INSERT INTO resource_action VALUES ('a6b5e8ee-a306-4907-9432-9b4a3f219a6c', '5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c');
INSERT INTO resource_action VALUES ('a6b5e8ee-a306-4907-9432-9b4a3f219a6c', '61d2d42f-8e56-47b1-b2bc-f7d2af9ad88b');
INSERT INTO resource_action VALUES ('a6b5e8ee-a306-4907-9432-9b4a3f219a6c', 'd5cba6cd-30ee-4ca5-901b-d3730d8011c1');
INSERT INTO resource_action VALUES ('a6b5e8ee-a306-4907-9432-9b4a3f219a6c', 'd079873c-fa53-41d4-b358-18f0e3b8f8b8');
INSERT INTO resource_action VALUES ('a6b5e8ee-a306-4907-9432-9b4a3f219a6c', '6c5e41ac-0d75-40b3-b37d-1cd00efbe137');
INSERT INTO resource_action VALUES ('a6b5e8ee-a306-4907-9432-9b4a3f219a6c', 'e236979b-d46a-4b7e-92e8-ab17d10d5ae4');
INSERT INTO resource_action VALUES ('a6b5e8ee-a306-4907-9432-9b4a3f219a6c', '2c69092e-71a7-46fa-b96f-e831b5886271');
INSERT INTO resource_action VALUES ('a6b5e8ee-a306-4907-9432-9b4a3f219a6c', '9a009d26-12fd-4450-9ba2-3bc541b6f7c4');
INSERT INTO resource_action VALUES ('a6b5e8ee-a306-4907-9432-9b4a3f219a6c', '017d401e-7f51-4992-92bc-701c78718538');
INSERT INTO resource_action VALUES ('69a132ef-70bd-4d5c-b7fc-ed562aeb8f4b', '5f8c0446-e51c-4ff2-9bb1-46d3abb7f18c');
INSERT INTO resource_action VALUES ('69a132ef-70bd-4d5c-b7fc-ed562aeb8f4b', '61d2d42f-8e56-47b1-b2bc-f7d2af9ad88b');
INSERT INTO resource_action VALUES ('69a132ef-70bd-4d5c-b7fc-ed562aeb8f4b', 'd5cba6cd-30ee-4ca5-901b-d3730d8011c1');
INSERT INTO resource_action VALUES ('69a132ef-70bd-4d5c-b7fc-ed562aeb8f4b', 'd079873c-fa53-41d4-b358-18f0e3b8f8b8');
INSERT INTO resource_action VALUES ('69a132ef-70bd-4d5c-b7fc-ed562aeb8f4b', 'b7a0cab3-ebf1-4199-8b2e-e2351309236d');
INSERT INTO resource_action VALUES ('69a132ef-70bd-4d5c-b7fc-ed562aeb8f4b', '8f26214f-59a0-4150-ba9f-aaf34a1cd5dc');
INSERT INTO resource_action VALUES ('029004b0-e3d9-4f75-a205-f1f73eddb65f', 'd079873c-fa53-41d4-b358-18f0e3b8f8b8');
INSERT INTO resource_action VALUES ('029004b0-e3d9-4f75-a205-f1f73eddb65f', 'd5cba6cd-30ee-4ca5-901b-d3730d8011c1');

--
-- Data for Name: resource_api_binding; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: resource_external_binding; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: resource_manifest_import; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: route_operation; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: schema_version; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO schema_version VALUES ('ou-authorization', '1', '2026-09-22 02:27:13.437067+00');
INSERT INTO schema_version VALUES ('resource-contract', '2', '2026-09-22 02:27:13.63486+00');
INSERT INTO schema_version VALUES ('outbound-connections', '1', '2026-09-22 02:27:13.660043+00');
INSERT INTO schema_version VALUES ('outbox-processor', '2', '2026-09-22 02:27:13.706032+00');
INSERT INTO schema_version VALUES ('ou-recalculation', '1', '2026-09-22 02:27:13.750534+00');
INSERT INTO schema_version VALUES ('ui-artifact-registry', '2', '2026-09-22 02:27:13.800533+00');
INSERT INTO schema_version VALUES ('identity-role-management', '2', '2026-09-22 02:27:13.828153+00');
INSERT INTO schema_version VALUES ('canonical-subject', '2', '2026-09-22 02:27:13.947608+00');
INSERT INTO schema_version VALUES ('openfga-object-serialization', '1', '2026-09-22 02:27:13.992569+00');
INSERT INTO schema_version VALUES ('openfga-idempotent-delete', '1', '2026-09-22 02:27:14.029119+00');
INSERT INTO schema_version VALUES ('effective-ui-catalog', '1', '2026-09-22 02:27:14.056701+00');
INSERT INTO schema_version VALUES ('admin-navigation-tooltips', '2', '2026-09-22 02:27:14.243599+00');
INSERT INTO schema_version VALUES ('mf-manifest', '1', '2026-09-22 02:27:14.285433+00');
INSERT INTO schema_version VALUES ('microfrontend-governance', '4', '2026-09-22 02:27:14.335185+00');
INSERT INTO schema_version VALUES ('canonical-identity', '3', '2026-09-22 02:27:14.412286+00');
INSERT INTO schema_version VALUES ('superset-registry', '4', '2026-09-22 02:27:14.576304+00');
INSERT INTO schema_version VALUES ('superset-resource-contract', '7', '2026-09-22 02:27:14.675805+00');
INSERT INTO schema_version VALUES ('directory-group-identity', '2', '2026-09-22 02:27:14.735088+00');
INSERT INTO schema_version VALUES ('superset-role-model', '5', '2026-09-22 02:27:14.928784+00');
INSERT INTO schema_version VALUES ('control-plane', '79', '2026-09-22 02:27:14.994506+00');
INSERT INTO schema_version VALUES ('admin-navigation-contract', '2', '2026-09-22 02:27:14.994506+00');

--
-- Data for Name: service_target; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: superset_access_sync; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: superset_asset; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: superset_instance; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: superset_proxy_mapping; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: superset_subject_mapping; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: ui_menu_override; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: ui_module_artifact; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO ui_module_artifact VALUES ('fc9e4fc8-4137-400a-8de8-40c4473b1e38', '84a0d6bd-2377-4c88-b9b2-138622a388e2', '0.2.0', 'http://localhost:3001/remoteEntry.js', 'aurevia_admin', './bootstrap', '1.0', '1.0', NULL, '{"menus": [{"id": "operator-guide-menu", "icon": "book", "order": 10, "title": "راهنمای فرم‌ها", "routeId": "operator-guide"}, {"id": "ou-access-ous-menu", "icon": "apartment", "order": 20, "title": "OUهای سازمانی", "routeId": "ou-access-ous"}, {"id": "ou-access-groups-menu", "icon": "team", "order": 21, "title": "Access Groupها", "routeId": "ou-access-groups"}, {"id": "ou-access-applications-menu", "icon": "appstore", "order": 22, "title": "دسترسی Microfrontend", "routeId": "ou-access-applications"}, {"id": "ou-access-explain-menu", "icon": "audit", "order": 23, "title": "بررسی دسترسی User", "routeId": "ou-access-explain"}, {"id": "access-studio-menu", "icon": "safety", "order": 30, "title": "استودیوی دسترسی", "routeId": "access-studio"}, {"id": "panels-menu", "icon": "appstore", "order": 40, "title": "میکروفرانت‌ها", "routeId": "panels"}, {"id": "proxy-targets-menu", "icon": "api", "order": 50, "title": "Service Targets", "routeId": "proxy-targets"}, {"id": "proxy-routes-menu", "icon": "branches", "order": 51, "title": "Proxy Routes", "routeId": "proxy-routes"}, {"id": "proxy-operations-menu", "icon": "control", "order": 52, "title": "Route Operations", "routeId": "proxy-operations"}, {"id": "outbound-connections-menu", "icon": "link", "order": 60, "title": "اتصال‌های Legacy", "routeId": "outbound-connections"}, {"id": "outbound-auth-menu", "icon": "key", "order": 70, "title": "پروفایل‌های احراز هویت سرویس‌ها", "routeId": "outbound-auth"}, {"id": "integration-test-menu", "icon": "experiment", "order": 80, "title": "آزمایشگاه اتصال", "routeId": "integration-test"}, {"id": "superset-instances-menu", "icon": "cloud-server", "order": 90, "title": "محیط‌های Superset", "routeId": "superset-instances"}, {"id": "identity-menu", "icon": "idcard", "order": 100, "title": "گروه‌ها و نقش‌ها", "routeId": "identity"}, {"id": "logs-api-menu", "icon": "file-search", "order": 110, "title": "API Logs", "routeId": "logs-api"}, {"id": "logs-audit-menu", "icon": "audit", "order": 111, "title": "Audit Logs", "routeId": "logs-audit"}, {"id": "superset-menu", "icon": "dashboard", "order": 120, "title": "گزارش‌ها و داشبوردها", "routeId": "superset"}], "routes": [{"id": "operator-guide", "path": "operator-guide", "title": "راهنمای فرم‌ها", "action": "admin", "resource": "application:aurevia"}, {"id": "ou-access-ous", "path": "ou-access/ous", "title": "OUهای سازمانی", "action": "admin", "resource": "application:aurevia"}, {"id": "ou-access-groups", "path": "ou-access/groups", "title": "Access Groupها", "action": "admin", "resource": "application:aurevia"}, {"id": "ou-access-applications", "path": "ou-access/applications", "title": "دسترسی Microfrontend", "action": "admin", "resource": "application:aurevia"}, {"id": "ou-access-explain", "path": "ou-access/explain", "title": "بررسی دسترسی User", "action": "admin", "resource": "application:aurevia"}, {"id": "access-studio", "path": "access-studio", "title": "استودیوی دسترسی", "action": "admin", "resource": "application:aurevia"}, {"id": "panels", "path": "panels", "title": "میکروفرانت‌ها", "action": "admin", "resource": "application:aurevia"}, {"id": "proxy-targets", "path": "proxy-routes/targets", "title": "Service Targets", "action": "admin", "resource": "proxy.target"}, {"id": "proxy-routes", "path": "proxy-routes/routes", "title": "Proxy Routes", "action": "admin", "resource": "proxy.route"}, {"id": "proxy-operations", "path": "proxy-routes/operations", "title": "Route Operations", "action": "admin", "resource": "proxy.operation"}, {"id": "outbound-connections", "path": "outbound-connections", "title": "اتصال‌های Legacy", "action": "admin", "resource": "integration.auth-profile"}, {"id": "outbound-auth", "path": "outbound-auth", "title": "پروفایل‌های احراز هویت سرویس‌ها", "action": "admin", "resource": "integration.auth-profile"}, {"id": "integration-test", "path": "integration-test", "title": "آزمایشگاه اتصال", "action": "test", "resource": "integration.auth-profile"}, {"id": "superset-instances", "path": "superset-instances", "title": "محیط‌های Superset", "action": "admin", "resource": "application:aurevia"}, {"id": "identity", "path": "identity", "title": "گروه‌ها و نقش‌ها", "action": "admin", "resource": "application:aurevia"}, {"id": "logs-api", "path": "logs/api", "title": "API Logs", "action": "view_api", "resource": "business_resource:public-zone-logs"}, {"id": "logs-audit", "path": "logs/audit", "title": "Audit Logs", "action": "view_audit", "resource": "business_resource:public-zone-logs"}, {"id": "superset", "path": "superset", "title": "گزارش‌ها و داشبوردها", "action": "view", "resource": "module:admin.superset-catalog"}], "runtime": {"apiBasePath": "/api/v1/admin"}, "moduleKey": "admin", "schemaVersion": "1.0", "defaultRouteId": "operator-guide"}', 'VALID', NULL, true, '2026-09-22 02:27:14.056701+00', 'migration-v48', '8d99780d56269241c34028a938e2b18570f70794696f2a82052621129df3c088', NULL, NULL);
INSERT INTO ui_module_artifact VALUES ('1010afdc-d427-4071-9b31-f55b1f8988e1', '84a0d6bd-2377-4c88-b9b2-138622a388e2', '0.3.0', 'http://localhost:3001/remoteEntry.js', 'aurevia_admin', './bootstrap', '1.0', '1.0', NULL, '{"menus": [{"id": "operator-guide-menu", "icon": "book", "order": 10, "title": "راهنمای راهبری", "routeId": "operator-guide", "description": "آموزش فیلدها، قواعد و سناریوهای کار با پنل مدیریت"}, {"id": "ou-access-ous-menu", "icon": "apartment", "order": 20, "title": "ساختار سازمانی", "routeId": "ou-access-ous", "description": "مشاهده OUهای همگام‌شده از Directory و اعضای سازمان"}, {"id": "ou-access-groups-menu", "icon": "team", "order": 21, "title": "گروه‌های دسترسی", "routeId": "ou-access-groups", "description": "ساخت گروه محاسباتی با قواعد EXACT یا SUBTREE روی OUها"}, {"id": "ou-access-applications-menu", "icon": "appstore", "order": 22, "title": "دسترسی برنامه‌ها", "routeId": "ou-access-applications", "description": "اعطای دسترسی مشاهده Microfrontend به گروه‌های سازمانی"}, {"id": "ou-access-explain-menu", "icon": "audit", "order": 23, "title": "تحلیل دسترسی کاربر", "routeId": "ou-access-explain", "description": "ردیابی مسیر کاربر، OU، گروه و برنامه برای توضیح تصمیم دسترسی"}, {"id": "access-studio-menu", "icon": "safety", "order": 30, "title": "استودیوی مجوزها", "routeId": "access-studio", "description": "مدیریت درخت منابع، عملیات و Grantهای OpenFGA"}, {"id": "panels-menu", "icon": "appstore", "order": 40, "title": "مدیریت میکروفرانت‌ها", "routeId": "panels", "description": "ثبت Panel، انتشار Artifact و مدیریت Manifest و Navigation"}, {"id": "proxy-targets-menu", "icon": "api", "order": 50, "title": "مقصدهای سرویس", "routeId": "proxy-targets", "description": "تعریف Gateway، مسیر پایه، محدودیت پاسخ و Health Check"}, {"id": "proxy-routes-menu", "icon": "branches", "order": 51, "title": "مسیرهای پروکسی", "routeId": "proxy-routes", "description": "اتصال namespace ورودی Microfrontend به مقصد سرویس"}, {"id": "proxy-operations-menu", "icon": "control", "order": 52, "title": "عملیات مسیرها", "routeId": "proxy-operations", "description": "تعریف Method، Path و Resource/Action موردنیاز هر API"}, {"id": "outbound-connections-menu", "icon": "link", "order": 60, "title": "اتصال‌های خروجی", "routeId": "outbound-connections", "description": "ثبت Originهای مجاز برای ارتباط امن با سرویس‌های بیرونی و Legacy"}, {"id": "outbound-auth-menu", "icon": "key", "order": 70, "title": "احراز هویت سرویس‌ها", "routeId": "outbound-auth", "description": "تعریف روش ارسال یا دریافت توکن بدون ذخیره مقدار Secret"}, {"id": "integration-test-menu", "icon": "experiment", "order": 80, "title": "آزمایش اتصال", "routeId": "integration-test", "description": "اجرای تست امن End-to-End اتصال، توکن و پاسخ سرویس مقصد"}, {"id": "superset-instances-menu", "icon": "cloud-server", "order": 90, "title": "محیط‌های گزارش‌گیری", "routeId": "superset-instances", "description": "مدیریت Instanceهای Public و Operation در Apache Superset"}, {"id": "identity-menu", "icon": "idcard", "order": 100, "title": "هویت‌ها و نقش‌ها", "routeId": "identity", "description": "مشاهده گروه‌های همگام، ساخت نقش و تخصیص آن به کاربران"}, {"id": "logs-api-menu", "icon": "file-search", "order": 110, "title": "گزارش درخواست‌های API", "routeId": "logs-api", "description": "جست‌وجوی درخواست‌ها، خطاها، زمان پاسخ و Correlation ID"}, {"id": "logs-audit-menu", "icon": "audit", "order": 111, "title": "گزارش رویدادهای راهبری", "routeId": "logs-audit", "description": "مشاهده تغییرات مدیریتی، عامل، هدف و نتیجه هر عملیات"}, {"id": "superset-menu", "icon": "dashboard", "order": 120, "title": "دسترسی گزارش‌ها", "routeId": "superset", "description": "تخصیص سطح دسترسی داشبوردها و گزارش‌های Superset"}], "routes": [{"id": "operator-guide", "path": "operator-guide", "title": "راهنمای فرم‌ها", "action": "admin", "resource": "application:aurevia"}, {"id": "ou-access-ous", "path": "ou-access/ous", "title": "OUهای سازمانی", "action": "admin", "resource": "application:aurevia"}, {"id": "ou-access-groups", "path": "ou-access/groups", "title": "Access Groupها", "action": "admin", "resource": "application:aurevia"}, {"id": "ou-access-applications", "path": "ou-access/applications", "title": "دسترسی Microfrontend", "action": "admin", "resource": "application:aurevia"}, {"id": "ou-access-explain", "path": "ou-access/explain", "title": "بررسی دسترسی User", "action": "admin", "resource": "application:aurevia"}, {"id": "access-studio", "path": "access-studio", "title": "استودیوی دسترسی", "action": "admin", "resource": "application:aurevia"}, {"id": "panels", "path": "panels", "title": "میکروفرانت‌ها", "action": "admin", "resource": "application:aurevia"}, {"id": "proxy-targets", "path": "proxy-routes/targets", "title": "Service Targets", "action": "admin", "resource": "proxy.target"}, {"id": "proxy-routes", "path": "proxy-routes/routes", "title": "Proxy Routes", "action": "admin", "resource": "proxy.route"}, {"id": "proxy-operations", "path": "proxy-routes/operations", "title": "Route Operations", "action": "admin", "resource": "proxy.operation"}, {"id": "outbound-connections", "path": "outbound-connections", "title": "اتصال‌های Legacy", "action": "admin", "resource": "integration.auth-profile"}, {"id": "outbound-auth", "path": "outbound-auth", "title": "پروفایل‌های احراز هویت سرویس‌ها", "action": "admin", "resource": "integration.auth-profile"}, {"id": "integration-test", "path": "integration-test", "title": "آزمایشگاه اتصال", "action": "test", "resource": "integration.auth-profile"}, {"id": "superset-instances", "path": "superset-instances", "title": "محیط‌های Superset", "action": "admin", "resource": "application:aurevia"}, {"id": "identity", "path": "identity", "title": "گروه‌ها و نقش‌ها", "action": "admin", "resource": "application:aurevia"}, {"id": "logs-api", "path": "logs/api", "title": "API Logs", "action": "view_api", "resource": "business_resource:public-zone-logs"}, {"id": "logs-audit", "path": "logs/audit", "title": "Audit Logs", "action": "view_audit", "resource": "business_resource:public-zone-logs"}, {"id": "superset", "path": "superset", "title": "گزارش‌ها و داشبوردها", "action": "view", "resource": "module:admin.superset-catalog"}], "runtime": {"apiBasePath": "/api/v1/admin"}, "moduleKey": "admin", "schemaVersion": "1.0", "defaultRouteId": "operator-guide"}', 'VALID', NULL, true, '2026-09-22 02:27:14.218592+00', 'migration-v53', 'c76220bf10a4ae2437e9a99df27bf69f85a9d3280645720c7f18d12104873855', NULL, NULL);
INSERT INTO ui_module_artifact VALUES ('1b156492-3672-4414-9487-a5d4b41fdb21', '84a0d6bd-2377-4c88-b9b2-138622a388e2', '0.4.0', 'http://localhost:3001/remoteEntry.js', 'aurevia_admin', './bootstrap', '1.0', '1.0', NULL, '{"menus": [{"id": "operator-guide-menu", "icon": "book", "order": 10, "title": "راهنما", "routeId": "operator-guide", "description": "آموزش فیلدها، قواعد و سناریوهای کار با پنل مدیریت"}, {"id": "ou-access-ous-menu", "icon": "apartment", "order": 20, "title": "واحدهای سازمانی", "routeId": "ou-access-ous", "description": "مشاهده OUهای همگام‌شده از Directory و اعضای سازمان"}, {"id": "ou-access-groups-menu", "icon": "team", "order": 21, "title": "گروه‌ها", "routeId": "ou-access-groups", "description": "ساخت گروه محاسباتی با قواعد EXACT یا SUBTREE روی OUها"}, {"id": "ou-access-applications-menu", "icon": "appstore", "order": 22, "title": "برنامه‌ها", "routeId": "ou-access-applications", "description": "اعطای دسترسی مشاهده Microfrontend به گروه‌های سازمانی"}, {"id": "ou-access-explain-menu", "icon": "audit", "order": 23, "title": "تحلیل دسترسی", "routeId": "ou-access-explain", "description": "ردیابی مسیر کاربر، OU، گروه و برنامه برای توضیح تصمیم دسترسی"}, {"id": "access-studio-menu", "icon": "safety", "order": 30, "title": "منابع و مجوزها", "routeId": "access-studio", "description": "مدیریت درخت منابع، عملیات و Grantهای OpenFGA"}, {"id": "panels-menu", "icon": "appstore", "order": 40, "title": "میکروفرانت‌ها", "routeId": "panels", "description": "ثبت Panel، انتشار Artifact و مدیریت Manifest و Navigation"}, {"id": "proxy-targets-menu", "icon": "api", "order": 50, "title": "مقصدها", "routeId": "proxy-targets", "description": "تعریف Gateway، مسیر پایه، محدودیت پاسخ و Health Check"}, {"id": "proxy-routes-menu", "icon": "branches", "order": 51, "title": "مسیرها", "routeId": "proxy-routes", "description": "اتصال namespace ورودی Microfrontend به مقصد سرویس"}, {"id": "proxy-operations-menu", "icon": "control", "order": 52, "title": "عملیات API", "routeId": "proxy-operations", "description": "تعریف Method، Path و Resource/Action موردنیاز هر API"}, {"id": "outbound-connections-menu", "icon": "link", "order": 60, "title": "اتصال‌ها", "routeId": "outbound-connections", "description": "ثبت Originهای مجاز برای ارتباط امن با سرویس‌های بیرونی و Legacy"}, {"id": "outbound-auth-menu", "icon": "key", "order": 70, "title": "احراز هویت", "routeId": "outbound-auth", "description": "تعریف روش ارسال یا دریافت توکن بدون ذخیره مقدار Secret"}, {"id": "integration-test-menu", "icon": "experiment", "order": 80, "title": "تست اتصال", "routeId": "integration-test", "description": "اجرای تست امن End-to-End اتصال، توکن و پاسخ سرویس مقصد"}, {"id": "superset-instances-menu", "icon": "cloud-server", "order": 90, "title": "محیط‌های گزارش", "routeId": "superset-instances", "description": "مدیریت Instanceهای Public و Operation در Apache Superset"}, {"id": "identity-menu", "icon": "idcard", "order": 100, "title": "هویت و نقش", "routeId": "identity", "description": "مشاهده گروه‌های همگام، ساخت نقش و تخصیص آن به کاربران"}, {"id": "logs-api-menu", "icon": "file-search", "order": 110, "title": "لاگ API", "routeId": "logs-api", "description": "جست‌وجوی درخواست‌ها، خطاها، زمان پاسخ و Correlation ID"}, {"id": "logs-audit-menu", "icon": "audit", "order": 111, "title": "لاگ راهبری", "routeId": "logs-audit", "description": "مشاهده تغییرات مدیریتی، عامل، هدف و نتیجه هر عملیات"}, {"id": "superset-menu", "icon": "dashboard", "order": 120, "title": "گزارش‌ها", "routeId": "superset", "description": "تخصیص سطح دسترسی داشبوردها و گزارش‌های Superset"}], "routes": [{"id": "operator-guide", "path": "operator-guide", "title": "راهنمای فرم‌ها", "action": "admin", "resource": "application:aurevia"}, {"id": "ou-access-ous", "path": "ou-access/ous", "title": "OUهای سازمانی", "action": "admin", "resource": "application:aurevia"}, {"id": "ou-access-groups", "path": "ou-access/groups", "title": "Access Groupها", "action": "admin", "resource": "application:aurevia"}, {"id": "ou-access-applications", "path": "ou-access/applications", "title": "دسترسی Microfrontend", "action": "admin", "resource": "application:aurevia"}, {"id": "ou-access-explain", "path": "ou-access/explain", "title": "بررسی دسترسی User", "action": "admin", "resource": "application:aurevia"}, {"id": "access-studio", "path": "access-studio", "title": "استودیوی دسترسی", "action": "admin", "resource": "application:aurevia"}, {"id": "panels", "path": "panels", "title": "میکروفرانت‌ها", "action": "admin", "resource": "application:aurevia"}, {"id": "proxy-targets", "path": "proxy-routes/targets", "title": "Service Targets", "action": "admin", "resource": "proxy.target"}, {"id": "proxy-routes", "path": "proxy-routes/routes", "title": "Proxy Routes", "action": "admin", "resource": "proxy.route"}, {"id": "proxy-operations", "path": "proxy-routes/operations", "title": "Route Operations", "action": "admin", "resource": "proxy.operation"}, {"id": "outbound-connections", "path": "outbound-connections", "title": "اتصال‌های Legacy", "action": "admin", "resource": "integration.auth-profile"}, {"id": "outbound-auth", "path": "outbound-auth", "title": "پروفایل‌های احراز هویت سرویس‌ها", "action": "admin", "resource": "integration.auth-profile"}, {"id": "integration-test", "path": "integration-test", "title": "آزمایشگاه اتصال", "action": "test", "resource": "integration.auth-profile"}, {"id": "superset-instances", "path": "superset-instances", "title": "محیط‌های Superset", "action": "admin", "resource": "application:aurevia"}, {"id": "identity", "path": "identity", "title": "گروه‌ها و نقش‌ها", "action": "admin", "resource": "application:aurevia"}, {"id": "logs-api", "path": "logs/api", "title": "API Logs", "action": "view_api", "resource": "business_resource:public-zone-logs"}, {"id": "logs-audit", "path": "logs/audit", "title": "Audit Logs", "action": "view_audit", "resource": "business_resource:public-zone-logs"}, {"id": "superset", "path": "superset", "title": "گزارش‌ها و داشبوردها", "action": "view", "resource": "module:admin.superset-catalog"}], "runtime": {"apiBasePath": "/api/v1/admin"}, "moduleKey": "admin", "schemaVersion": "1.0", "defaultRouteId": "operator-guide"}', 'VALID', NULL, true, '2026-09-22 02:27:14.243599+00', 'migration-v54', '09c9fc5374197c8309835bb2fa0872a7946bda1d806964c6e128ea70faba8f14', NULL, NULL);
INSERT INTO ui_module_artifact VALUES ('654478ce-8174-4f7a-8572-feb7c65d36e5', '84a0d6bd-2377-4c88-b9b2-138622a388e2', '0.1.0', 'http://localhost:3001/remoteEntry.js', 'aurevia_admin', './bootstrap', '1.0', '1.0', NULL, '{"menus": [{"id": "main", "icon": "appstore", "order": 10, "title": "مدیریت", "routeId": "index"}], "routes": [{"id": "index", "path": "", "title": "مدیریت", "action": "view", "resource": "application:aurevia/admin"}], "moduleKey": "admin", "schemaVersion": "1.0", "defaultRouteId": "index"}', 'VALID', NULL, true, '2026-09-22 02:27:13.488935+00', 'migration', '6da73b834abe2f98565630081af8758a3b6e5d011aa5cdad80182af65bae7933', NULL, NULL);
INSERT INTO ui_module_artifact VALUES ('138d5376-1a3d-48c0-a5b6-52a7c9b9baf9', '84a0d6bd-2377-4c88-b9b2-138622a388e2', '0.5.0', 'http://localhost:3001/remoteEntry.js', 'aurevia_admin', './bootstrap', '1.0', '1.0', NULL, '{"routes": [{"key": "operator-guide", "path": "operator-guide", "title": "راهنما", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "ou-access-ous", "path": "ou-access/ous", "title": "واحدهای سازمانی", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "ou-access-groups", "path": "ou-access/groups", "title": "گروه‌ها", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "ou-access-applications", "path": "ou-access/applications", "title": "برنامه‌ها", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "ou-access-explain", "path": "ou-access/explain", "title": "تحلیل دسترسی", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "access-studio", "path": "access-studio", "title": "منابع و مجوزها", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "panels", "path": "panels", "title": "میکروفرانت‌ها", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "proxy-targets", "path": "proxy-routes/targets", "title": "مقصدها", "requiredAction": "admin", "requiredResource": "proxy.target"}, {"key": "proxy-routes", "path": "proxy-routes/routes", "title": "مسیرها", "requiredAction": "admin", "requiredResource": "proxy.route"}, {"key": "proxy-operations", "path": "proxy-routes/operations", "title": "عملیات API", "requiredAction": "admin", "requiredResource": "proxy.operation"}, {"key": "outbound-connections", "path": "outbound-connections", "title": "اتصال‌ها", "requiredAction": "admin", "requiredResource": "integration.auth-profile"}, {"key": "outbound-auth", "path": "outbound-auth", "title": "احراز هویت", "requiredAction": "admin", "requiredResource": "integration.auth-profile"}, {"key": "integration-test", "path": "integration-test", "title": "تست اتصال", "requiredAction": "test", "requiredResource": "integration.auth-profile"}, {"key": "superset-instances", "path": "superset-instances", "title": "محیط‌های گزارش", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "identity", "path": "identity", "title": "هویت و نقش", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "logs-api", "path": "logs/api", "title": "لاگ API", "requiredAction": "view_api", "requiredResource": "business_resource:public-zone-logs"}, {"key": "logs-audit", "path": "logs/audit", "title": "لاگ راهبری", "requiredAction": "view_audit", "requiredResource": "business_resource:public-zone-logs"}, {"key": "superset", "path": "superset", "title": "گزارش‌ها", "requiredAction": "view", "requiredResource": "module:admin.superset-catalog"}], "runtime": {"remoteName": "aurevia_admin", "apiBasePath": "/api/v1/admin", "remoteEntry": "http://localhost:3001/remoteEntry.js", "exposedModule": "./bootstrap", "contractVersion": "1.0"}, "navigation": [{"key": "operator-guide-menu", "icon": "book", "type": "PAGE", "order": 10, "title": "راهنما", "routeKey": "operator-guide", "description": "آموزش فیلدها، قواعد و سناریوهای کار با پنل مدیریت"}, {"key": "ou-access-ous-menu", "icon": "apartment", "type": "PAGE", "order": 20, "title": "واحدهای سازمانی", "routeKey": "ou-access-ous", "description": "مشاهده OUهای همگام‌شده از Directory و اعضای سازمان"}, {"key": "ou-access-groups-menu", "icon": "team", "type": "PAGE", "order": 21, "title": "گروه‌ها", "routeKey": "ou-access-groups", "description": "ساخت گروه محاسباتی با قواعد EXACT یا SUBTREE روی OUها"}, {"key": "ou-access-applications-menu", "icon": "appstore", "type": "PAGE", "order": 22, "title": "برنامه‌ها", "routeKey": "ou-access-applications", "description": "اعطای دسترسی مشاهده Microfrontend به گروه‌های سازمانی"}, {"key": "ou-access-explain-menu", "icon": "audit", "type": "PAGE", "order": 23, "title": "تحلیل دسترسی", "routeKey": "ou-access-explain", "description": "ردیابی مسیر کاربر، OU، گروه و برنامه برای توضیح تصمیم دسترسی"}, {"key": "access-studio-menu", "icon": "safety", "type": "PAGE", "order": 30, "title": "منابع و مجوزها", "routeKey": "access-studio", "description": "مدیریت درخت منابع، عملیات و Grantهای OpenFGA"}, {"key": "panels-menu", "icon": "appstore", "type": "PAGE", "order": 40, "title": "میکروفرانت‌ها", "routeKey": "panels", "description": "ثبت Panel، انتشار Artifact و مدیریت Manifest و Navigation"}, {"key": "proxy-targets-menu", "icon": "api", "type": "PAGE", "order": 50, "title": "مقصدها", "routeKey": "proxy-targets", "description": "تعریف Gateway، مسیر پایه، محدودیت پاسخ و Health Check"}, {"key": "proxy-routes-menu", "icon": "branches", "type": "PAGE", "order": 51, "title": "مسیرها", "routeKey": "proxy-routes", "description": "اتصال namespace ورودی Microfrontend به مقصد سرویس"}, {"key": "proxy-operations-menu", "icon": "control", "type": "PAGE", "order": 52, "title": "عملیات API", "routeKey": "proxy-operations", "description": "تعریف Method، Path و Resource/Action موردنیاز هر API"}, {"key": "outbound-connections-menu", "icon": "link", "type": "PAGE", "order": 60, "title": "اتصال‌ها", "routeKey": "outbound-connections", "description": "ثبت Originهای مجاز برای ارتباط امن با سرویس‌های بیرونی و Legacy"}, {"key": "outbound-auth-menu", "icon": "key", "type": "PAGE", "order": 70, "title": "احراز هویت", "routeKey": "outbound-auth", "description": "تعریف روش ارسال یا دریافت توکن بدون ذخیره مقدار Secret"}, {"key": "integration-test-menu", "icon": "experiment", "type": "PAGE", "order": 80, "title": "تست اتصال", "routeKey": "integration-test", "description": "اجرای تست امن End-to-End اتصال، توکن و پاسخ سرویس مقصد"}, {"key": "superset-instances-menu", "icon": "cloud-server", "type": "PAGE", "order": 90, "title": "محیط‌های گزارش", "routeKey": "superset-instances", "description": "مدیریت Instanceهای Public و Operation در Apache Superset"}, {"key": "identity-menu", "icon": "idcard", "type": "PAGE", "order": 100, "title": "هویت و نقش", "routeKey": "identity", "description": "مشاهده گروه‌های همگام، ساخت نقش و تخصیص آن به کاربران"}, {"key": "logs-api-menu", "icon": "file-search", "type": "PAGE", "order": 110, "title": "لاگ API", "routeKey": "logs-api", "description": "جست‌وجوی درخواست‌ها، خطاها، زمان پاسخ و Correlation ID"}, {"key": "logs-audit-menu", "icon": "audit", "type": "PAGE", "order": 111, "title": "لاگ راهبری", "routeKey": "logs-audit", "description": "مشاهده تغییرات مدیریتی، عامل، هدف و نتیجه هر عملیات"}, {"key": "superset-menu", "icon": "dashboard", "type": "PAGE", "order": 120, "title": "گزارش‌ها", "routeKey": "superset", "description": "تخصیص سطح دسترسی داشبوردها و گزارش‌های Superset"}], "microfrontend": {"key": "admin", "name": "Administration", "version": "0.5.0"}, "schemaVersion": "1.0", "defaultRouteKey": "operator-guide"}', 'VALID', NULL, true, '2026-09-22 02:27:14.312576+00', 'migration-v56', 'e3aebf2e0b0b8127e2454497b9cb79da4abcf92c0b77be22a2b5db5ca1d5d877', 'http://localhost:3001/mf-manifest.json', '2026-09-22 02:27:14.312576+00');
INSERT INTO ui_module_artifact VALUES ('de76f4b6-7176-4e3c-b129-3d7928308218', '84a0d6bd-2377-4c88-b9b2-138622a388e2', '0.6.0', 'http://localhost:3001/remoteEntry.js', 'aurevia_admin', './bootstrap', '1.0', '1.0', NULL, '{"routes": [{"key": "operator-guide", "path": "operator-guide", "title": "راهنما", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "ou-access-ous", "path": "ou-access/ous", "title": "واحدهای سازمانی", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "ou-access-groups", "path": "ou-access/groups", "title": "گروه‌ها", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "ou-access-applications", "path": "ou-access/applications", "title": "برنامه‌ها", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "ou-access-explain", "path": "ou-access/explain", "title": "تحلیل دسترسی", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "access-studio", "path": "access-studio", "title": "منابع و مجوزها", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "panels", "path": "panels", "title": "میکروفرانت‌ها", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "proxy-targets", "path": "proxy-routes/targets", "title": "مقصدها", "requiredAction": "admin", "requiredResource": "proxy.target"}, {"key": "proxy-routes", "path": "proxy-routes/routes", "title": "مسیرها", "requiredAction": "admin", "requiredResource": "proxy.route"}, {"key": "proxy-operations", "path": "proxy-routes/operations", "title": "عملیات API", "requiredAction": "admin", "requiredResource": "proxy.operation"}, {"key": "outbound-connections", "path": "outbound-connections", "title": "اتصال‌ها", "requiredAction": "admin", "requiredResource": "integration.auth-profile"}, {"key": "outbound-auth", "path": "outbound-auth", "title": "احراز هویت", "requiredAction": "admin", "requiredResource": "integration.auth-profile"}, {"key": "integration-test", "path": "integration-test", "title": "تست اتصال", "requiredAction": "test", "requiredResource": "integration.auth-profile"}, {"key": "superset-instances", "path": "superset-instances", "title": "محیط‌های گزارش", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "users", "path": "users", "title": "مدیریت کاربران", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "identity", "path": "identity", "title": "هویت و نقش", "requiredAction": "admin", "requiredResource": "application:aurevia"}, {"key": "logs-api", "path": "logs/api", "title": "لاگ API", "requiredAction": "view_api", "requiredResource": "business_resource:public-zone-logs"}, {"key": "logs-audit", "path": "logs/audit", "title": "لاگ راهبری", "requiredAction": "view_audit", "requiredResource": "business_resource:public-zone-logs"}, {"key": "superset", "path": "superset", "title": "گزارش‌ها", "requiredAction": "view", "requiredResource": "module:admin.superset-catalog"}], "runtime": {"remoteName": "aurevia_admin", "apiBasePath": "/api/v1/admin", "remoteEntry": "http://localhost:3001/remoteEntry.js", "exposedModule": "./bootstrap", "contractVersion": "1.0"}, "navigation": [{"key": "operator-guide-menu", "icon": "book", "type": "PAGE", "order": 10, "title": "راهنما", "routeKey": "operator-guide", "description": "آموزش فیلدها، قواعد و سناریوهای کار با پنل مدیریت"}, {"key": "ou-access-ous-menu", "icon": "apartment", "type": "PAGE", "order": 20, "title": "واحدهای سازمانی", "routeKey": "ou-access-ous", "description": "مشاهده OUهای همگام‌شده از Directory و اعضای سازمان"}, {"key": "ou-access-groups-menu", "icon": "team", "type": "PAGE", "order": 21, "title": "گروه‌ها", "routeKey": "ou-access-groups", "description": "ساخت گروه محاسباتی با قواعد EXACT یا SUBTREE روی OUها"}, {"key": "ou-access-applications-menu", "icon": "appstore", "type": "PAGE", "order": 22, "title": "برنامه‌ها", "routeKey": "ou-access-applications", "description": "اعطای دسترسی مشاهده Microfrontend به گروه‌های سازمانی"}, {"key": "ou-access-explain-menu", "icon": "audit", "type": "PAGE", "order": 23, "title": "تحلیل دسترسی", "routeKey": "ou-access-explain", "description": "ردیابی مسیر کاربر، OU، گروه و برنامه برای توضیح تصمیم دسترسی"}, {"key": "access-studio-menu", "icon": "safety", "type": "PAGE", "order": 30, "title": "منابع و مجوزها", "routeKey": "access-studio", "description": "مدیریت درخت منابع، عملیات و Grantهای OpenFGA"}, {"key": "panels-menu", "icon": "appstore", "type": "PAGE", "order": 40, "title": "میکروفرانت‌ها", "routeKey": "panels", "description": "ثبت Panel، انتشار Artifact و مدیریت Manifest و Navigation"}, {"key": "proxy-targets-menu", "icon": "api", "type": "PAGE", "order": 50, "title": "مقصدها", "routeKey": "proxy-targets", "description": "تعریف Gateway، مسیر پایه، محدودیت پاسخ و Health Check"}, {"key": "proxy-routes-menu", "icon": "branches", "type": "PAGE", "order": 51, "title": "مسیرها", "routeKey": "proxy-routes", "description": "اتصال namespace ورودی Microfrontend به مقصد سرویس"}, {"key": "proxy-operations-menu", "icon": "control", "type": "PAGE", "order": 52, "title": "عملیات API", "routeKey": "proxy-operations", "description": "تعریف Method، Path و Resource/Action موردنیاز هر API"}, {"key": "outbound-connections-menu", "icon": "link", "type": "PAGE", "order": 60, "title": "اتصال‌ها", "routeKey": "outbound-connections", "description": "ثبت Originهای مجاز برای ارتباط امن با سرویس‌های بیرونی و Legacy"}, {"key": "outbound-auth-menu", "icon": "key", "type": "PAGE", "order": 70, "title": "احراز هویت", "routeKey": "outbound-auth", "description": "تعریف روش ارسال یا دریافت توکن بدون ذخیره مقدار Secret"}, {"key": "integration-test-menu", "icon": "experiment", "type": "PAGE", "order": 80, "title": "تست اتصال", "routeKey": "integration-test", "description": "اجرای تست امن End-to-End اتصال، توکن و پاسخ سرویس مقصد"}, {"key": "superset-instances-menu", "icon": "cloud-server", "type": "PAGE", "order": 90, "title": "محیط‌های گزارش", "routeKey": "superset-instances", "description": "مدیریت Instanceهای Public و Operation در Apache Superset"}, {"key": "users-menu", "icon": "user-add", "type": "PAGE", "order": 95, "title": "مدیریت کاربران", "routeKey": "users", "description": "ایجاد کاربر جدید در Keycloak از پنل مدیریت بدون دسترسی مستقیم مرورگر به Keycloak"}, {"key": "identity-menu", "icon": "idcard", "type": "PAGE", "order": 100, "title": "هویت و نقش", "routeKey": "identity", "description": "مشاهده گروه‌های همگام، ساخت نقش و تخصیص آن به کاربران"}, {"key": "logs-api-menu", "icon": "file-search", "type": "PAGE", "order": 110, "title": "لاگ API", "routeKey": "logs-api", "description": "جست‌وجوی درخواست‌ها، خطاها، زمان پاسخ و Correlation ID"}, {"key": "logs-audit-menu", "icon": "audit", "type": "PAGE", "order": 111, "title": "لاگ راهبری", "routeKey": "logs-audit", "description": "مشاهده تغییرات مدیریتی، عامل، هدف و نتیجه هر عملیات"}, {"key": "superset-menu", "icon": "dashboard", "type": "PAGE", "order": 120, "title": "گزارش‌ها", "routeKey": "superset", "description": "تخصیص سطح دسترسی داشبوردها و گزارش‌های Superset"}], "microfrontend": {"key": "admin", "name": "Administration", "version": "0.6.0"}, "schemaVersion": "1.0", "defaultRouteKey": "operator-guide"}', 'VALID', NULL, true, '2026-09-22 02:27:14.994506+00', 'migration-v79', 'ecc540a2066671bea310d6a6d999cc8947089c3b6289a6c2330fdadb0049d1f9', 'http://localhost:3001/mf-manifest.json', '2026-09-22 02:27:14.994506+00');

--
-- Data for Name: user_group_membership; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: user_ou_assignment; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Data for Name: user_role_assignment; Type: TABLE DATA; Schema: public; Owner: -
--

--
-- Name: outbox_event_sequence_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('outbox_event_sequence_seq', 647, true);

--
-- Name: access_group access_group_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY access_group
    ADD CONSTRAINT access_group_code_key UNIQUE (code);

--
-- Name: access_group_ou_rule access_group_ou_rule_access_group_id_ou_id_match_mode_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY access_group_ou_rule
    ADD CONSTRAINT access_group_ou_rule_access_group_id_ou_id_match_mode_key UNIQUE (access_group_id, ou_id, match_mode);

--
-- Name: access_group_ou_rule access_group_ou_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY access_group_ou_rule
    ADD CONSTRAINT access_group_ou_rule_pkey PRIMARY KEY (id);

--
-- Name: access_group access_group_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY access_group
    ADD CONSTRAINT access_group_pkey PRIMARY KEY (id);

--
-- Name: access_group_role_assignment access_group_role_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY access_group_role_assignment
    ADD CONSTRAINT access_group_role_assignment_pkey PRIMARY KEY (access_group_id, role_id);

--
-- Name: action action_action_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY action
    ADD CONSTRAINT action_action_key_key UNIQUE (action_key);

--
-- Name: action action_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY action
    ADD CONSTRAINT action_pkey PRIMARY KEY (id);

--
-- Name: api_log api_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY api_log
    ADD CONSTRAINT api_log_pkey PRIMARY KEY (id);

--
-- Name: app_user app_user_canonical_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY app_user
    ADD CONSTRAINT app_user_canonical_user_id_key UNIQUE (canonical_user_id);

--
-- Name: app_user app_user_issuer_external_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY app_user
    ADD CONSTRAINT app_user_issuer_external_id_key UNIQUE (issuer, external_id);

--
-- Name: app_user app_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);

--
-- Name: application_group_grant application_group_grant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY application_group_grant
    ADD CONSTRAINT application_group_grant_pkey PRIMARY KEY (id);

--
-- Name: application_role application_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY application_role
    ADD CONSTRAINT application_role_pkey PRIMARY KEY (id);

--
-- Name: application_role application_role_role_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY application_role
    ADD CONSTRAINT application_role_role_key_key UNIQUE (role_key);

--
-- Name: audit_event audit_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY audit_event
    ADD CONSTRAINT audit_event_pkey PRIMARY KEY (id);

--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);

--
-- Name: authorization_decision_log authorization_decision_log_decision_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY authorization_decision_log
    ADD CONSTRAINT authorization_decision_log_decision_id_key UNIQUE (decision_id);

--
-- Name: authorization_decision_log authorization_decision_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY authorization_decision_log
    ADD CONSTRAINT authorization_decision_log_pkey PRIMARY KEY (id);

--
-- Name: authorization_grant authorization_grant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY authorization_grant
    ADD CONSTRAINT authorization_grant_pkey PRIMARY KEY (id);

--
-- Name: condition_definition condition_definition_condition_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY condition_definition
    ADD CONSTRAINT condition_definition_condition_key_key UNIQUE (condition_key);

--
-- Name: condition_definition condition_definition_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY condition_definition
    ADD CONSTRAINT condition_definition_pkey PRIMARY KEY (id);

--
-- Name: data_policy data_policy_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_policy
    ADD CONSTRAINT data_policy_pkey PRIMARY KEY (id);

--
-- Name: data_policy data_policy_policy_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_policy
    ADD CONSTRAINT data_policy_policy_key_key UNIQUE (policy_key);

--
-- Name: directory_group directory_group_issuer_external_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY directory_group
    ADD CONSTRAINT directory_group_issuer_external_id_key UNIQUE (issuer, external_id);

--
-- Name: directory_group directory_group_issuer_normalized_path_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY directory_group
    ADD CONSTRAINT directory_group_issuer_normalized_path_key UNIQUE (issuer, normalized_path);

--
-- Name: directory_group directory_group_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY directory_group
    ADD CONSTRAINT directory_group_pkey PRIMARY KEY (id);

--
-- Name: directory_ou directory_ou_issuer_external_dn_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY directory_ou
    ADD CONSTRAINT directory_ou_issuer_external_dn_key UNIQUE (issuer, external_dn);

--
-- Name: directory_ou directory_ou_issuer_external_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY directory_ou
    ADD CONSTRAINT directory_ou_issuer_external_id_key UNIQUE (issuer, external_id);

--
-- Name: directory_ou directory_ou_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY directory_ou
    ADD CONSTRAINT directory_ou_pkey PRIMARY KEY (id);

--
-- Name: directory_sync_run directory_sync_run_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY directory_sync_run
    ADD CONSTRAINT directory_sync_run_pkey PRIMARY KEY (id);

--
-- Name: effective_group_membership effective_group_membership_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY effective_group_membership
    ADD CONSTRAINT effective_group_membership_pkey PRIMARY KEY (id);

--
-- Name: effective_group_membership effective_group_membership_user_id_access_group_id_source_t_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY effective_group_membership
    ADD CONSTRAINT effective_group_membership_user_id_access_group_id_source_t_key UNIQUE (user_id, access_group_id, source_type, source_id);

--
-- Name: external_identity external_identity_issuer_subject_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY external_identity
    ADD CONSTRAINT external_identity_issuer_subject_key UNIQUE (issuer, subject);

--
-- Name: external_identity external_identity_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY external_identity
    ADD CONSTRAINT external_identity_pkey PRIMARY KEY (id);

--
-- Name: group_role_assignment group_role_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY group_role_assignment
    ADD CONSTRAINT group_role_assignment_pkey PRIMARY KEY (group_id, role_id);

--
-- Name: identity_provider identity_provider_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY identity_provider
    ADD CONSTRAINT identity_provider_code_key UNIQUE (code);

--
-- Name: identity_provider identity_provider_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY identity_provider
    ADD CONSTRAINT identity_provider_pkey PRIMARY KEY (id);

--
-- Name: ou_recalculation_job ou_recalculation_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ou_recalculation_job
    ADD CONSTRAINT ou_recalculation_job_pkey PRIMARY KEY (id);

--
-- Name: outbound_auth_profile outbound_auth_profile_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY outbound_auth_profile
    ADD CONSTRAINT outbound_auth_profile_code_key UNIQUE (code);

--
-- Name: outbound_auth_profile outbound_auth_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY outbound_auth_profile
    ADD CONSTRAINT outbound_auth_profile_pkey PRIMARY KEY (id);

--
-- Name: outbound_connection outbound_connection_connection_ref_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY outbound_connection
    ADD CONSTRAINT outbound_connection_connection_ref_key UNIQUE (connection_ref);

--
-- Name: outbound_connection outbound_connection_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY outbound_connection
    ADD CONSTRAINT outbound_connection_pkey PRIMARY KEY (id);

--
-- Name: outbox_event outbox_event_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY outbox_event
    ADD CONSTRAINT outbox_event_idempotency_key_key UNIQUE (idempotency_key);

--
-- Name: outbox_event outbox_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id);

--
-- Name: panel panel_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY panel
    ADD CONSTRAINT panel_code_key UNIQUE (code);

--
-- Name: panel panel_name_en_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY panel
    ADD CONSTRAINT panel_name_en_key UNIQUE (name_en);

--
-- Name: panel panel_name_fa_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY panel
    ADD CONSTRAINT panel_name_fa_key UNIQUE (name_fa);

--
-- Name: panel panel_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY panel
    ADD CONSTRAINT panel_pkey PRIMARY KEY (id);

--
-- Name: panel panel_route_base_path_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY panel
    ADD CONSTRAINT panel_route_base_path_key UNIQUE (route_base_path);

--
-- Name: panel panel_slug_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY panel
    ADD CONSTRAINT panel_slug_key UNIQUE (slug);

--
-- Name: proxy_route proxy_route_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY proxy_route
    ADD CONSTRAINT proxy_route_pkey PRIMARY KEY (id);

--
-- Name: proxy_route proxy_route_route_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY proxy_route
    ADD CONSTRAINT proxy_route_route_key_key UNIQUE (code);

--
-- Name: resource_action resource_action_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource_action
    ADD CONSTRAINT resource_action_pkey PRIMARY KEY (resource_id, action_id);

--
-- Name: resource_api_binding resource_api_binding_http_method_path_pattern_service_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource_api_binding
    ADD CONSTRAINT resource_api_binding_http_method_path_pattern_service_code_key UNIQUE (http_method, path_pattern, service_code);

--
-- Name: resource_api_binding resource_api_binding_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource_api_binding
    ADD CONSTRAINT resource_api_binding_pkey PRIMARY KEY (id);

--
-- Name: resource_external_binding resource_external_binding_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource_external_binding
    ADD CONSTRAINT resource_external_binding_pkey PRIMARY KEY (id);

--
-- Name: resource_external_binding resource_external_binding_provider_external_type_external_i_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource_external_binding
    ADD CONSTRAINT resource_external_binding_provider_external_type_external_i_key UNIQUE (provider, external_type, external_id);

--
-- Name: resource_external_binding resource_external_binding_resource_id_provider_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource_external_binding
    ADD CONSTRAINT resource_external_binding_resource_id_provider_key UNIQUE (resource_id, provider);

--
-- Name: resource resource_external_system_external_type_external_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource
    ADD CONSTRAINT resource_external_system_external_type_external_id_key UNIQUE (external_system, external_type, external_id);

--
-- Name: resource_manifest_import resource_manifest_import_application_key_manifest_version_c_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource_manifest_import
    ADD CONSTRAINT resource_manifest_import_application_key_manifest_version_c_key UNIQUE (application_key, manifest_version, checksum);

--
-- Name: resource_manifest_import resource_manifest_import_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource_manifest_import
    ADD CONSTRAINT resource_manifest_import_pkey PRIMARY KEY (id);

--
-- Name: resource resource_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource
    ADD CONSTRAINT resource_pkey PRIMARY KEY (id);

--
-- Name: resource resource_resource_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource
    ADD CONSTRAINT resource_resource_key_key UNIQUE (resource_key);

--
-- Name: route_operation route_operation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY route_operation
    ADD CONSTRAINT route_operation_pkey PRIMARY KEY (id);

--
-- Name: route_operation route_operation_route_id_http_method_relative_pattern_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY route_operation
    ADD CONSTRAINT route_operation_route_id_http_method_relative_pattern_key UNIQUE (proxy_route_id, http_method, path_pattern);

--
-- Name: schema_version schema_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY schema_version
    ADD CONSTRAINT schema_version_pkey PRIMARY KEY (component);

--
-- Name: service_target service_target_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_target
    ADD CONSTRAINT service_target_pkey PRIMARY KEY (id);

--
-- Name: service_target service_target_target_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_target
    ADD CONSTRAINT service_target_target_key_key UNIQUE (code);

--
-- Name: superset_access_sync superset_access_sync_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_access_sync
    ADD CONSTRAINT superset_access_sync_idempotency_key_key UNIQUE (idempotency_key);

--
-- Name: superset_access_sync superset_access_sync_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_access_sync
    ADD CONSTRAINT superset_access_sync_pkey PRIMARY KEY (id);

--
-- Name: superset_asset superset_asset_instance_external_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_asset
    ADD CONSTRAINT superset_asset_instance_external_unique UNIQUE (instance_id, asset_type, external_id);

--
-- Name: superset_asset superset_asset_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_asset
    ADD CONSTRAINT superset_asset_pkey PRIMARY KEY (id);

--
-- Name: superset_asset superset_asset_resource_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_asset
    ADD CONSTRAINT superset_asset_resource_id_key UNIQUE (resource_id);

--
-- Name: superset_instance superset_instance_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_instance
    ADD CONSTRAINT superset_instance_code_key UNIQUE (code);

--
-- Name: superset_instance superset_instance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_instance
    ADD CONSTRAINT superset_instance_pkey PRIMARY KEY (id);

--
-- Name: superset_proxy_mapping superset_proxy_mapping_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_proxy_mapping
    ADD CONSTRAINT superset_proxy_mapping_pkey PRIMARY KEY (id);

--
-- Name: superset_proxy_mapping superset_proxy_mapping_public_instance_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_proxy_mapping
    ADD CONSTRAINT superset_proxy_mapping_public_instance_id_key UNIQUE (public_instance_id);

--
-- Name: superset_proxy_mapping superset_proxy_mapping_public_path_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_proxy_mapping
    ADD CONSTRAINT superset_proxy_mapping_public_path_key UNIQUE (public_path);

--
-- Name: superset_subject_mapping superset_subject_mapping_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_subject_mapping
    ADD CONSTRAINT superset_subject_mapping_pkey PRIMARY KEY (id);

--
-- Name: superset_subject_mapping superset_subject_mapping_subject_type_subject_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_subject_mapping
    ADD CONSTRAINT superset_subject_mapping_subject_type_subject_id_key UNIQUE (subject_type, subject_id);

--
-- Name: ui_menu_override ui_menu_override_panel_id_menu_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_menu_override
    ADD CONSTRAINT ui_menu_override_panel_id_menu_id_key UNIQUE (panel_id, menu_id);

--
-- Name: ui_menu_override ui_menu_override_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_menu_override
    ADD CONSTRAINT ui_menu_override_pkey PRIMARY KEY (id);

--
-- Name: ui_module_artifact ui_module_artifact_panel_id_artifact_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_module_artifact
    ADD CONSTRAINT ui_module_artifact_panel_id_artifact_version_key UNIQUE (panel_id, artifact_version);

--
-- Name: ui_module_artifact ui_module_artifact_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_module_artifact
    ADD CONSTRAINT ui_module_artifact_pkey PRIMARY KEY (id);

--
-- Name: user_group_membership user_group_membership_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_group_membership
    ADD CONSTRAINT user_group_membership_pkey PRIMARY KEY (user_id, group_id);

--
-- Name: user_ou_assignment user_ou_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_ou_assignment
    ADD CONSTRAINT user_ou_assignment_pkey PRIMARY KEY (id);

--
-- Name: user_role_assignment user_role_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_role_assignment
    ADD CONSTRAINT user_role_assignment_pkey PRIMARY KEY (user_id, role_id);

--
-- Name: access_group_ou_rule_ou_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX access_group_ou_rule_ou_idx ON access_group_ou_rule USING btree (ou_id) WHERE active;

--
-- Name: active_application_group_grant_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX active_application_group_grant_idx ON application_group_grant USING btree (application_id, access_group_id, relation) WHERE (revoked_at IS NULL);

--
-- Name: api_log_correlation_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX api_log_correlation_idx ON api_log USING btree (correlation_id);

--
-- Name: api_log_event_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX api_log_event_time_idx ON api_log USING btree (event_time DESC);

--
-- Name: api_log_route_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX api_log_route_time_idx ON api_log USING btree (route_template, event_time DESC);

--
-- Name: api_log_service_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX api_log_service_time_idx ON api_log USING btree (service_name, event_time DESC);

--
-- Name: api_log_status_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX api_log_status_time_idx ON api_log USING btree (status_code, event_time DESC);

--
-- Name: api_log_user_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX api_log_user_time_idx ON api_log USING btree (user_id, event_time DESC);

--
-- Name: app_user_directory_external_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX app_user_directory_external_idx ON app_user USING btree (issuer, directory_external_id) WHERE (directory_external_id IS NOT NULL);

--
-- Name: app_user_subject_key_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX app_user_subject_key_idx ON app_user USING btree (subject_key);

--
-- Name: audit_log_actor_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_log_actor_time_idx ON audit_log USING btree (actor_id, event_time DESC);

--
-- Name: audit_log_correlation_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_log_correlation_idx ON audit_log USING btree (correlation_id);

--
-- Name: audit_log_event_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_log_event_time_idx ON audit_log USING btree (event_time DESC);

--
-- Name: audit_log_event_type_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_log_event_type_time_idx ON audit_log USING btree (event_type, event_time DESC);

--
-- Name: audit_log_target_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_log_target_time_idx ON audit_log USING btree (target_type, target_id, event_time DESC);

--
-- Name: authorization_decision_correlation_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX authorization_decision_correlation_idx ON authorization_decision_log USING btree (correlation_id, decided_at DESC);

--
-- Name: authorization_decision_lookup_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX authorization_decision_lookup_idx ON authorization_decision_log USING btree (subject_key, resource_key, action_key, decided_at DESC);

--
-- Name: authorization_decision_retention_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX authorization_decision_retention_idx ON authorization_decision_log USING btree (decided_at);

--
-- Name: authorization_grant_active_unique_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX authorization_grant_active_unique_idx ON authorization_grant USING btree (subject_type, subject_id, resource_id, action_id) WHERE (status = 'ACTIVE'::lifecycle_status);

--
-- Name: authorization_grant_lookup_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX authorization_grant_lookup_idx ON authorization_grant USING btree (subject_type, subject_id, resource_id, action_id) WHERE (status = 'ACTIVE'::lifecycle_status);

--
-- Name: directory_ou_parent_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX directory_ou_parent_idx ON directory_ou USING btree (parent_ou_id);

--
-- Name: directory_ou_path_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX directory_ou_path_idx ON directory_ou USING btree (issuer, external_path) WHERE active;

--
-- Name: effective_membership_lookup_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX effective_membership_lookup_idx ON effective_group_membership USING btree (user_id, access_group_id) WHERE active;

--
-- Name: external_identity_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX external_identity_user_idx ON external_identity USING btree (user_id);

--
-- Name: identity_provider_routing_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX identity_provider_routing_idx ON identity_provider USING btree (tenant_id, enabled, connection_status);

--
-- Name: ou_recalculation_active_group_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ou_recalculation_active_group_idx ON ou_recalculation_job USING btree (access_group_id) WHERE (status = ANY (ARRAY['PENDING'::recalculation_job_status, 'RUNNING'::recalculation_job_status]));

--
-- Name: ou_recalculation_pending_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ou_recalculation_pending_idx ON ou_recalculation_job USING btree (available_at, created_at) WHERE (status = 'PENDING'::recalculation_job_status);

--
-- Name: outbox_claimable_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX outbox_claimable_idx ON outbox_event USING btree (available_at, sequence) WHERE ((processed_at IS NULL) AND (dead_lettered_at IS NULL));

--
-- Name: outbox_dead_letter_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX outbox_dead_letter_idx ON outbox_event USING btree (dead_lettered_at) WHERE (dead_lettered_at IS NOT NULL);

--
-- Name: outbox_pending_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX outbox_pending_idx ON outbox_event USING btree (available_at, created_at) WHERE ((processed_at IS NULL) AND (dead_lettered_at IS NULL));

--
-- Name: outbox_sequence_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX outbox_sequence_idx ON outbox_event USING btree (sequence);

--
-- Name: proxy_route_outbound_auth_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX proxy_route_outbound_auth_idx ON proxy_route USING btree (outbound_auth_profile_id);

--
-- Name: proxy_route_resolution_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX proxy_route_resolution_idx ON proxy_route USING btree (active, normalized_path_prefix, priority DESC);

--
-- Name: resource_manifest_panel_version_unique_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX resource_manifest_panel_version_unique_idx ON resource_manifest_import USING btree (panel_id, manifest_version) WHERE (panel_id IS NOT NULL);

--
-- Name: resource_manifest_panel_workflow_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX resource_manifest_panel_workflow_idx ON resource_manifest_import USING btree (panel_id, workflow_status, created_at DESC);

--
-- Name: resource_panel_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX resource_panel_idx ON resource USING btree (panel_id, status, resource_key);

--
-- Name: route_operation_resolution_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX route_operation_resolution_idx ON route_operation USING btree (proxy_route_id, http_method, active);

--
-- Name: superset_asset_instance_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX superset_asset_instance_idx ON superset_asset USING btree (instance_id, published);

--
-- Name: superset_single_default_mapping_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX superset_single_default_mapping_idx ON superset_proxy_mapping USING btree (is_default) WHERE (is_default AND active);

--
-- Name: ui_module_artifact_panel_checksum_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ui_module_artifact_panel_checksum_idx ON ui_module_artifact USING btree (panel_id, manifest_checksum);

--
-- Name: ui_module_artifact_panel_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ui_module_artifact_panel_idx ON ui_module_artifact USING btree (panel_id, created_at DESC);

--
-- Name: ui_module_artifact_remote_name_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ui_module_artifact_remote_name_idx ON ui_module_artifact USING btree (remote_name);

--
-- Name: user_ou_assignment_ou_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_ou_assignment_ou_idx ON user_ou_assignment USING btree (ou_id) WHERE active;

--
-- Name: user_primary_ad_ou_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX user_primary_ad_ou_idx ON user_ou_assignment USING btree (user_id) WHERE (active AND (source = 'ACTIVE_DIRECTORY'::directory_source));

--
-- Name: access_group_ou_rule access_group_ou_rule_access_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY access_group_ou_rule
    ADD CONSTRAINT access_group_ou_rule_access_group_id_fkey FOREIGN KEY (access_group_id) REFERENCES access_group(id) ON DELETE CASCADE;

--
-- Name: access_group_ou_rule access_group_ou_rule_ou_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY access_group_ou_rule
    ADD CONSTRAINT access_group_ou_rule_ou_id_fkey FOREIGN KEY (ou_id) REFERENCES directory_ou(id) ON DELETE RESTRICT;

--
-- Name: access_group_role_assignment access_group_role_assignment_access_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY access_group_role_assignment
    ADD CONSTRAINT access_group_role_assignment_access_group_id_fkey FOREIGN KEY (access_group_id) REFERENCES access_group(id) ON DELETE RESTRICT;

--
-- Name: access_group_role_assignment access_group_role_assignment_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY access_group_role_assignment
    ADD CONSTRAINT access_group_role_assignment_role_id_fkey FOREIGN KEY (role_id) REFERENCES application_role(id) ON DELETE RESTRICT;

--
-- Name: application_group_grant application_group_grant_access_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY application_group_grant
    ADD CONSTRAINT application_group_grant_access_group_id_fkey FOREIGN KEY (access_group_id) REFERENCES access_group(id) ON DELETE RESTRICT;

--
-- Name: application_group_grant application_group_grant_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY application_group_grant
    ADD CONSTRAINT application_group_grant_application_id_fkey FOREIGN KEY (application_id) REFERENCES panel(id) ON DELETE RESTRICT;

--
-- Name: authorization_grant authorization_grant_action_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY authorization_grant
    ADD CONSTRAINT authorization_grant_action_id_fkey FOREIGN KEY (action_id) REFERENCES action(id) ON DELETE RESTRICT;

--
-- Name: authorization_grant authorization_grant_condition_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY authorization_grant
    ADD CONSTRAINT authorization_grant_condition_fk FOREIGN KEY (condition_id) REFERENCES condition_definition(id) ON DELETE RESTRICT;

--
-- Name: authorization_grant authorization_grant_resource_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY authorization_grant
    ADD CONSTRAINT authorization_grant_resource_id_fkey FOREIGN KEY (resource_id) REFERENCES resource(id) ON DELETE RESTRICT;

--
-- Name: data_policy data_policy_action_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_policy
    ADD CONSTRAINT data_policy_action_id_fkey FOREIGN KEY (action_id) REFERENCES action(id) ON DELETE RESTRICT;

--
-- Name: data_policy data_policy_condition_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_policy
    ADD CONSTRAINT data_policy_condition_id_fkey FOREIGN KEY (condition_id) REFERENCES condition_definition(id) ON DELETE RESTRICT;

--
-- Name: data_policy data_policy_resource_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY data_policy
    ADD CONSTRAINT data_policy_resource_id_fkey FOREIGN KEY (resource_id) REFERENCES resource(id) ON DELETE RESTRICT;

--
-- Name: directory_group directory_group_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY directory_group
    ADD CONSTRAINT directory_group_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES directory_group(id) ON DELETE RESTRICT;

--
-- Name: directory_ou directory_ou_parent_ou_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY directory_ou
    ADD CONSTRAINT directory_ou_parent_ou_id_fkey FOREIGN KEY (parent_ou_id) REFERENCES directory_ou(id) ON DELETE RESTRICT;

--
-- Name: effective_group_membership effective_group_membership_access_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY effective_group_membership
    ADD CONSTRAINT effective_group_membership_access_group_id_fkey FOREIGN KEY (access_group_id) REFERENCES access_group(id) ON DELETE CASCADE;

--
-- Name: effective_group_membership effective_group_membership_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY effective_group_membership
    ADD CONSTRAINT effective_group_membership_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE;

--
-- Name: external_identity external_identity_identity_provider_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY external_identity
    ADD CONSTRAINT external_identity_identity_provider_id_fkey FOREIGN KEY (identity_provider_id) REFERENCES identity_provider(id) ON DELETE RESTRICT;

--
-- Name: external_identity external_identity_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY external_identity
    ADD CONSTRAINT external_identity_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE;

--
-- Name: group_role_assignment group_role_assignment_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY group_role_assignment
    ADD CONSTRAINT group_role_assignment_group_id_fkey FOREIGN KEY (group_id) REFERENCES directory_group(id) ON DELETE RESTRICT;

--
-- Name: group_role_assignment group_role_assignment_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY group_role_assignment
    ADD CONSTRAINT group_role_assignment_role_id_fkey FOREIGN KEY (role_id) REFERENCES application_role(id) ON DELETE RESTRICT;

--
-- Name: ou_recalculation_job ou_recalculation_job_access_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ou_recalculation_job
    ADD CONSTRAINT ou_recalculation_job_access_group_id_fkey FOREIGN KEY (access_group_id) REFERENCES access_group(id);

--
-- Name: outbound_auth_profile outbound_auth_profile_connection_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY outbound_auth_profile
    ADD CONSTRAINT outbound_auth_profile_connection_fk FOREIGN KEY (token_connection_ref) REFERENCES outbound_connection(connection_ref);

--
-- Name: panel panel_active_artifact_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY panel
    ADD CONSTRAINT panel_active_artifact_id_fkey FOREIGN KEY (active_artifact_id) REFERENCES ui_module_artifact(id) ON DELETE RESTRICT;

--
-- Name: panel panel_discovery_resource_key_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY panel
    ADD CONSTRAINT panel_discovery_resource_key_fkey FOREIGN KEY (discovery_resource_key) REFERENCES resource(resource_key) ON DELETE RESTRICT;

--
-- Name: proxy_route proxy_route_outbound_auth_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY proxy_route
    ADD CONSTRAINT proxy_route_outbound_auth_profile_id_fkey FOREIGN KEY (outbound_auth_profile_id) REFERENCES outbound_auth_profile(id);

--
-- Name: proxy_route proxy_route_panel_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY proxy_route
    ADD CONSTRAINT proxy_route_panel_id_fkey FOREIGN KEY (panel_id) REFERENCES panel(id) ON DELETE RESTRICT;

--
-- Name: proxy_route proxy_route_target_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY proxy_route
    ADD CONSTRAINT proxy_route_target_id_fkey FOREIGN KEY (service_target_id) REFERENCES service_target(id) ON DELETE RESTRICT;

--
-- Name: resource_action resource_action_action_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource_action
    ADD CONSTRAINT resource_action_action_id_fkey FOREIGN KEY (action_id) REFERENCES action(id) ON DELETE RESTRICT;

--
-- Name: resource_action resource_action_resource_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource_action
    ADD CONSTRAINT resource_action_resource_id_fkey FOREIGN KEY (resource_id) REFERENCES resource(id) ON DELETE RESTRICT;

--
-- Name: resource_api_binding resource_api_binding_action_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource_api_binding
    ADD CONSTRAINT resource_api_binding_action_id_fkey FOREIGN KEY (action_id) REFERENCES action(id) ON DELETE RESTRICT;

--
-- Name: resource_api_binding resource_api_binding_resource_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource_api_binding
    ADD CONSTRAINT resource_api_binding_resource_id_fkey FOREIGN KEY (resource_id) REFERENCES resource(id) ON DELETE RESTRICT;

--
-- Name: resource_external_binding resource_external_binding_resource_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource_external_binding
    ADD CONSTRAINT resource_external_binding_resource_id_fkey FOREIGN KEY (resource_id) REFERENCES resource(id) ON DELETE RESTRICT;

--
-- Name: resource_manifest_import resource_manifest_import_panel_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource_manifest_import
    ADD CONSTRAINT resource_manifest_import_panel_id_fkey FOREIGN KEY (panel_id) REFERENCES panel(id) ON DELETE RESTRICT;

--
-- Name: resource resource_panel_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource
    ADD CONSTRAINT resource_panel_id_fkey FOREIGN KEY (panel_id) REFERENCES panel(id) ON DELETE RESTRICT;

--
-- Name: resource resource_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY resource
    ADD CONSTRAINT resource_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES resource(id) ON DELETE RESTRICT;

--
-- Name: route_operation route_operation_action_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY route_operation
    ADD CONSTRAINT route_operation_action_id_fkey FOREIGN KEY (action_id) REFERENCES action(id) ON DELETE RESTRICT;

--
-- Name: route_operation route_operation_resource_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY route_operation
    ADD CONSTRAINT route_operation_resource_id_fkey FOREIGN KEY (resource_id) REFERENCES resource(id) ON DELETE RESTRICT;

--
-- Name: route_operation route_operation_route_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY route_operation
    ADD CONSTRAINT route_operation_route_id_fkey FOREIGN KEY (proxy_route_id) REFERENCES proxy_route(id) ON DELETE CASCADE;

--
-- Name: service_target service_target_outbound_auth_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY service_target
    ADD CONSTRAINT service_target_outbound_auth_profile_id_fkey FOREIGN KEY (outbound_auth_profile_id) REFERENCES outbound_auth_profile(id);

--
-- Name: superset_access_sync superset_access_sync_grant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_access_sync
    ADD CONSTRAINT superset_access_sync_grant_id_fkey FOREIGN KEY (grant_id) REFERENCES authorization_grant(id) ON DELETE RESTRICT;

--
-- Name: superset_asset superset_asset_instance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_asset
    ADD CONSTRAINT superset_asset_instance_id_fkey FOREIGN KEY (instance_id) REFERENCES superset_instance(id);

--
-- Name: superset_asset superset_asset_resource_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_asset
    ADD CONSTRAINT superset_asset_resource_id_fkey FOREIGN KEY (resource_id) REFERENCES resource(id) ON DELETE RESTRICT;

--
-- Name: superset_proxy_mapping superset_proxy_mapping_operation_instance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_proxy_mapping
    ADD CONSTRAINT superset_proxy_mapping_operation_instance_id_fkey FOREIGN KEY (operation_instance_id) REFERENCES superset_instance(id) ON DELETE RESTRICT;

--
-- Name: superset_proxy_mapping superset_proxy_mapping_public_instance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY superset_proxy_mapping
    ADD CONSTRAINT superset_proxy_mapping_public_instance_id_fkey FOREIGN KEY (public_instance_id) REFERENCES superset_instance(id) ON DELETE RESTRICT;

--
-- Name: ui_menu_override ui_menu_override_panel_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_menu_override
    ADD CONSTRAINT ui_menu_override_panel_id_fkey FOREIGN KEY (panel_id) REFERENCES panel(id) ON DELETE CASCADE;

--
-- Name: ui_module_artifact ui_module_artifact_panel_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ui_module_artifact
    ADD CONSTRAINT ui_module_artifact_panel_id_fkey FOREIGN KEY (panel_id) REFERENCES panel(id) ON DELETE RESTRICT;

--
-- Name: user_group_membership user_group_membership_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_group_membership
    ADD CONSTRAINT user_group_membership_group_id_fkey FOREIGN KEY (group_id) REFERENCES directory_group(id) ON DELETE CASCADE;

--
-- Name: user_group_membership user_group_membership_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_group_membership
    ADD CONSTRAINT user_group_membership_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE;

--
-- Name: user_ou_assignment user_ou_assignment_ou_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_ou_assignment
    ADD CONSTRAINT user_ou_assignment_ou_id_fkey FOREIGN KEY (ou_id) REFERENCES directory_ou(id) ON DELETE RESTRICT;

--
-- Name: user_ou_assignment user_ou_assignment_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_ou_assignment
    ADD CONSTRAINT user_ou_assignment_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE;

--
-- Name: user_role_assignment user_role_assignment_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_role_assignment
    ADD CONSTRAINT user_role_assignment_role_id_fkey FOREIGN KEY (role_id) REFERENCES application_role(id) ON DELETE RESTRICT;

--
-- Name: user_role_assignment user_role_assignment_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY user_role_assignment
    ADD CONSTRAINT user_role_assignment_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT;

--
-- PostgreSQL database dump complete
--


