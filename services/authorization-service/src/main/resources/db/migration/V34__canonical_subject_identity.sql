-- A username is mutable and a subject is not globally unique without its issuer.
-- Store a deterministic, non-reversible issuer+sub key for all OpenFGA user tuples.
CREATE OR REPLACE FUNCTION aurevia_subject_key(p_issuer text, p_subject text)
RETURNS text
LANGUAGE sql
IMMUTABLE
STRICT
PARALLEL SAFE
AS $$
  SELECT 'v1:' || encode(digest(
    convert_to(trim(p_issuer), 'UTF8') || decode('00', 'hex') ||
    convert_to(trim(p_subject), 'UTF8'), 'sha256'), 'hex')
$$;

ALTER TABLE app_user
  ADD COLUMN subject_key varchar(67)
  GENERATED ALWAYS AS (aurevia_subject_key(issuer, external_id)) STORED;

CREATE UNIQUE INDEX app_user_subject_key_idx ON app_user(subject_key);

-- Reproject every user-owned tuple. Old unscoped tuples are removed first so a
-- username or a subject from another issuer cannot inherit stale access.
INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-subject',u.id,'GRANT_DELETE',
  jsonb_build_object('user','user:'||u.external_id,'relation',case a.action_key
      when 'view' then 'viewer' when 'list' then 'viewer'
      when 'update' then 'editor' when 'approve' then 'editor' when 'reject' then 'editor'
      when 'delete' then 'manager' when 'share' then 'sharer' when 'export' then 'exporter'
      when 'admin' then 'manager' when 'manage' then 'manager' else g.relation end,
    'object',case
      when r.type='APPLICATION' then 'application:'||regexp_replace(r.resource_key,'^application:','')
      when r.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(r.resource_key,'^external_resource:',''),':','/')
      else 'resource:'||replace(r.resource_key,':','/') end),
  'CANONICAL_SUBJECT:GRANT:DELETE:'||g.id
FROM authorization_grant g
JOIN app_user u ON g.subject_type='USER' AND u.id=g.subject_id
JOIN resource r ON r.id=g.resource_id
JOIN action a ON a.id=g.action_id
WHERE g.status='ACTIVE' AND (g.expires_at IS NULL OR g.expires_at>now())
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-subject',u.id,'GRANT_WRITE',
  jsonb_build_object('user','user:'||u.subject_key,'relation',case a.action_key
      when 'view' then 'viewer' when 'list' then 'viewer'
      when 'update' then 'editor' when 'approve' then 'editor' when 'reject' then 'editor'
      when 'delete' then 'manager' when 'share' then 'sharer' when 'export' then 'exporter'
      when 'admin' then 'manager' when 'manage' then 'manager' else g.relation end,
    'object',case
      when r.type='APPLICATION' then 'application:'||regexp_replace(r.resource_key,'^application:','')
      when r.type='EXTERNAL_RESOURCE' then 'external_resource:'||replace(regexp_replace(r.resource_key,'^external_resource:',''),':','/')
      else 'resource:'||replace(r.resource_key,':','/') end),
  'CANONICAL_SUBJECT:GRANT:WRITE:'||g.id
FROM authorization_grant g
JOIN app_user u ON g.subject_type='USER' AND u.id=g.subject_id
JOIN resource r ON r.id=g.resource_id
JOIN action a ON a.id=g.action_id
WHERE g.status='ACTIVE' AND (g.expires_at IS NULL OR g.expires_at>now())
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-subject',u.id,'GROUP_MEMBERSHIP_DELETE',
  jsonb_build_object('user','user:'||u.external_id,'relation','member','object','group:'||g.external_id),
  'CANONICAL_SUBJECT:DIRECTORY:DELETE:'||u.id||':'||g.id
FROM user_group_membership m JOIN app_user u ON u.id=m.user_id
JOIN directory_group g ON g.id=m.group_id
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-subject',u.id,'GROUP_MEMBERSHIP_WRITE',
  jsonb_build_object('user','user:'||u.subject_key,'relation','member','object','group:'||g.external_id),
  'CANONICAL_SUBJECT:DIRECTORY:WRITE:'||u.id||':'||g.id
FROM user_group_membership m JOIN app_user u ON u.id=m.user_id
JOIN directory_group g ON g.id=m.group_id
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-subject',u.id,'ACCESS_GROUP_MEMBERSHIP_DELETE',
  jsonb_build_object('user','user:'||u.external_id,'relation','member','object','group:'||lower(g.code)),
  'CANONICAL_SUBJECT:ACCESS:DELETE:'||u.id||':'||g.id
FROM effective_group_membership m JOIN app_user u ON u.id=m.user_id
JOIN access_group g ON g.id=m.access_group_id
WHERE m.active AND g.active
GROUP BY u.id,u.external_id,g.id,g.code
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-subject',u.id,'ACCESS_GROUP_MEMBERSHIP_WRITE',
  jsonb_build_object('user','user:'||u.subject_key,'relation','member','object','group:'||lower(g.code)),
  'CANONICAL_SUBJECT:ACCESS:WRITE:'||u.id||':'||g.id
FROM effective_group_membership m JOIN app_user u ON u.id=m.user_id
JOIN access_group g ON g.id=m.access_group_id
WHERE m.active AND g.active
GROUP BY u.id,u.subject_key,g.id,g.code
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-subject',u.id,'ROLE_ASSIGNMENT_DELETE',
  jsonb_build_object('user','user:'||u.external_id,'relation','assignee','object','role:'||r.role_key),
  'CANONICAL_SUBJECT:ROLE:DELETE:'||u.id||':'||r.id
FROM user_role_assignment a JOIN app_user u ON u.id=a.user_id
JOIN application_role r ON r.id=a.role_id
WHERE a.expires_at IS NULL OR a.expires_at>now()
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'canonical-subject',u.id,'ROLE_ASSIGNMENT_WRITE',
  jsonb_build_object('user','user:'||u.subject_key,'relation','assignee','object','role:'||r.role_key),
  'CANONICAL_SUBJECT:ROLE:WRITE:'||u.id||':'||r.id
FROM user_role_assignment a JOIN app_user u ON u.id=a.user_id
JOIN application_role r ON r.id=a.role_id
WHERE a.expires_at IS NULL OR a.expires_at>now()
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES ('canonical-subject','1')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
