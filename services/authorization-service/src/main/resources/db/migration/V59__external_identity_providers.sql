CREATE TABLE identity_provider (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code varchar(80) NOT NULL UNIQUE,
  name varchar(255) NOT NULL,
  provider_type varchar(40) NOT NULL,
  issuer_url varchar(2048) NOT NULL,
  authorization_endpoint varchar(2048) NOT NULL,
  token_endpoint varchar(2048) NOT NULL,
  jwks_uri varchar(2048) NOT NULL,
  user_info_endpoint varchar(2048),
  client_id varchar(255) NOT NULL,
  client_secret_reference varchar(512) NOT NULL,
  enabled boolean NOT NULL DEFAULT true,
  tenant_id varchar(160),
  domains varchar(255)[] NOT NULL DEFAULT '{}',
  scopes varchar(160)[] NOT NULL DEFAULT ARRAY['openid','profile','email']::varchar(160)[],
  audiences varchar(255)[] NOT NULL DEFAULT '{}',
  subject_claim varchar(160) NOT NULL DEFAULT 'sub',
  username_claim varchar(160) NOT NULL DEFAULT 'preferred_username',
  groups_claim varchar(160) NOT NULL DEFAULT 'groups',
  connection_status varchar(40) NOT NULL DEFAULT 'UNKNOWN',
  last_health_check_at timestamptz,
  last_health_error varchar(500),
  version bigint NOT NULL DEFAULT 0,
  created_by varchar(500) NOT NULL,
  updated_by varchar(500) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT identity_provider_code_format CHECK (code ~ '^[a-z][a-z0-9-]{2,79}$'),
  CONSTRAINT identity_provider_type_check CHECK (provider_type IN
    ('OIDC','KEYCLOAK','AZURE_AD','OKTA','AUTH0','GOOGLE_WORKSPACE')),
  CONSTRAINT identity_provider_status_check CHECK (connection_status IN
    ('UNKNOWN','ACTIVE','UNREACHABLE','INVALID'))
);
CREATE INDEX identity_provider_routing_idx
  ON identity_provider(tenant_id,enabled,connection_status);

-- app_user is the canonical authorization principal. External issuer/sub pairs
-- are aliases and may be replaced without changing this identifier or OpenFGA tuples.
ALTER TABLE app_user ADD COLUMN canonical_user_id varchar(80);
UPDATE app_user SET canonical_user_id='usr_'||replace(id::text,'-','');
ALTER TABLE app_user ALTER COLUMN canonical_user_id SET NOT NULL;
ALTER TABLE app_user ALTER COLUMN canonical_user_id
  SET DEFAULT ('usr_'||replace(gen_random_uuid()::text,'-',''));
ALTER TABLE app_user ADD CONSTRAINT app_user_canonical_user_id_key UNIQUE(canonical_user_id);

CREATE TABLE external_identity (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  identity_provider_id uuid REFERENCES identity_provider(id) ON DELETE RESTRICT,
  issuer varchar(2048) NOT NULL,
  subject varchar(512) NOT NULL,
  last_login_at timestamptz,
  created_by varchar(500) NOT NULL DEFAULT 'migration',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(issuer,subject)
);
CREATE INDEX external_identity_user_idx ON external_identity(user_id);
INSERT INTO external_identity(user_id,issuer,subject,last_login_at)
SELECT id,issuer,external_id,updated_at FROM app_user;

-- Rewrite pending events before deleting old projected relationships.
UPDATE outbox_event event
SET payload=jsonb_set(event.payload,'{user}',to_jsonb('user:'||u.canonical_user_id))
FROM app_user u
WHERE event.processed_at IS NULL AND event.payload->>'user'='user:'||u.subject_key;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-identity',u.id,'GRANT_DELETE',jsonb_build_object(
  'user','user:'||u.subject_key,'relation',case a.action_key
    when 'view' then 'viewer' when 'list' then 'viewer'
    when 'update' then 'editor' when 'approve' then 'editor' when 'reject' then 'editor'
    when 'delete' then 'manager' when 'share' then 'sharer' when 'export' then 'exporter'
    when 'admin' then 'manager' when 'manage' then 'manager' else g.relation end,
  'object',case when r.type='APPLICATION' then 'application:'||regexp_replace(r.resource_key,'^application:','')
    when r.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(r.resource_key,'^external_resource:',''),':','/')
    else 'resource:'||replace(r.resource_key,':','/') end),
  'CANONICAL_IDENTITY:GRANT:DELETE:'||g.id
FROM authorization_grant g JOIN app_user u ON g.subject_type='USER' AND u.id=g.subject_id
JOIN resource r ON r.id=g.resource_id JOIN action a ON a.id=g.action_id
WHERE g.status='ACTIVE' AND (g.expires_at IS NULL OR g.expires_at>now())
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-identity',u.id,'GRANT_WRITE',jsonb_build_object(
  'user','user:'||u.canonical_user_id,'relation',case a.action_key
    when 'view' then 'viewer' when 'list' then 'viewer'
    when 'update' then 'editor' when 'approve' then 'editor' when 'reject' then 'editor'
    when 'delete' then 'manager' when 'share' then 'sharer' when 'export' then 'exporter'
    when 'admin' then 'manager' when 'manage' then 'manager' else g.relation end,
  'object',case when r.type='APPLICATION' then 'application:'||regexp_replace(r.resource_key,'^application:','')
    when r.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(r.resource_key,'^external_resource:',''),':','/')
    else 'resource:'||replace(r.resource_key,':','/') end),
  'CANONICAL_IDENTITY:GRANT:WRITE:'||g.id
FROM authorization_grant g JOIN app_user u ON g.subject_type='USER' AND u.id=g.subject_id
JOIN resource r ON r.id=g.resource_id JOIN action a ON a.id=g.action_id
WHERE g.status='ACTIVE' AND (g.expires_at IS NULL OR g.expires_at>now())
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-identity',u.id,'GROUP_MEMBERSHIP_DELETE',
  jsonb_build_object('user','user:'||u.subject_key,'relation','member','object','group:'||g.external_id),
  'CANONICAL_IDENTITY:DIRECTORY:DELETE:'||u.id||':'||g.id
FROM user_group_membership m JOIN app_user u ON u.id=m.user_id
JOIN directory_group g ON g.id=m.group_id ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-identity',u.id,'GROUP_MEMBERSHIP_WRITE',
  jsonb_build_object('user','user:'||u.canonical_user_id,'relation','member','object','group:'||g.external_id),
  'CANONICAL_IDENTITY:DIRECTORY:WRITE:'||u.id||':'||g.id
FROM user_group_membership m JOIN app_user u ON u.id=m.user_id
JOIN directory_group g ON g.id=m.group_id ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-identity',u.id,'ACCESS_GROUP_MEMBERSHIP_DELETE',
  jsonb_build_object('user','user:'||u.subject_key,'relation','member','object','group:'||lower(g.code)),
  'CANONICAL_IDENTITY:ACCESS:DELETE:'||u.id||':'||g.id
FROM effective_group_membership m JOIN app_user u ON u.id=m.user_id
JOIN access_group g ON g.id=m.access_group_id WHERE m.active AND g.active
GROUP BY u.id,u.subject_key,g.id,g.code ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-identity',u.id,'ACCESS_GROUP_MEMBERSHIP_WRITE',
  jsonb_build_object('user','user:'||u.canonical_user_id,'relation','member','object','group:'||lower(g.code)),
  'CANONICAL_IDENTITY:ACCESS:WRITE:'||u.id||':'||g.id
FROM effective_group_membership m JOIN app_user u ON u.id=m.user_id
JOIN access_group g ON g.id=m.access_group_id WHERE m.active AND g.active
GROUP BY u.id,u.canonical_user_id,g.id,g.code ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-identity',u.id,'ROLE_ASSIGNMENT_DELETE',
  jsonb_build_object('user','user:'||u.subject_key,'relation','assignee','object','role:'||r.role_key),
  'CANONICAL_IDENTITY:ROLE:DELETE:'||u.id||':'||r.id
FROM user_role_assignment a JOIN app_user u ON u.id=a.user_id
JOIN application_role r ON r.id=a.role_id WHERE a.expires_at IS NULL OR a.expires_at>now()
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-identity',u.id,'ROLE_ASSIGNMENT_WRITE',
  jsonb_build_object('user','user:'||u.canonical_user_id,'relation','assignee','object','role:'||r.role_key),
  'CANONICAL_IDENTITY:ROLE:WRITE:'||u.id||':'||r.id
FROM user_role_assignment a JOIN app_user u ON u.id=a.user_id
JOIN application_role r ON r.id=a.role_id WHERE a.expires_at IS NULL OR a.expires_at>now()
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES ('canonical-identity','3')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
