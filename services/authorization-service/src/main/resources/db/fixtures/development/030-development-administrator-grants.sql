-- DEVELOPMENT fixture: give the bootstrapped first administrator the demo grants that the
-- historical demo identity (Keycloak sub literally 'administrator', retired by V78) used to hold,
-- so the local demo (HR/Finance pages, demo Superset assets, integration demo) is fully usable.
--
-- Deterministic and dev-only: the source is the fixture identity, the target is the identity the
-- one-time First Administrator Bootstrap created (external_identity.created_by='FIRST_ADMIN_BOOTSTRAP').
-- Production never loads this file; there the administrator grants demo access, if any, explicitly.
\set ON_ERROR_STOP on
BEGIN;

WITH source_user AS (
  SELECT u.id FROM app_user u
  WHERE u.issuer='http://localhost:8180/realms/aurevia' AND u.external_id='administrator'
), target_user AS (
  SELECT e.user_id AS id FROM external_identity e JOIN app_user u ON u.id=e.user_id
  WHERE e.created_by='FIRST_ADMIN_BOOTSTRAP' AND u.status='ACTIVE'
  ORDER BY e.created_at LIMIT 1
), wanted AS (
  SELECT DISTINCT ON (g.resource_id,g.action_id) g.resource_id,g.action_id,g.relation
  FROM authorization_grant g JOIN source_user s ON g.subject_type='USER' AND g.subject_id=s.id
  JOIN resource r ON r.id=g.resource_id AND r.status='ACTIVE'
  ORDER BY g.resource_id,g.action_id,(g.status='ACTIVE') DESC,g.created_at DESC
)
INSERT INTO authorization_grant(id,subject_type,subject_id,resource_id,action_id,relation)
SELECT gen_random_uuid(),'USER',t.id,w.resource_id,w.action_id,w.relation
FROM wanted w CROSS JOIN target_user t
ON CONFLICT(subject_type,subject_id,resource_id,action_id) WHERE status='ACTIVE' DO NOTHING;

-- Integration demo pages registered by 020-development-integration-catalog.sql.
INSERT INTO authorization_grant(id,subject_type,subject_id,resource_id,action_id,relation)
SELECT gen_random_uuid(),'USER',t.id,r.id,a.id,'viewer'
FROM resource r CROSS JOIN action a CROSS JOIN (
  SELECT e.user_id AS id FROM external_identity e JOIN app_user u ON u.id=e.user_id
  WHERE e.created_by='FIRST_ADMIN_BOOTSTRAP' AND u.status='ACTIVE' ORDER BY e.created_at LIMIT 1) t
WHERE r.resource_key IN ('api:integration.legacy-demo','api:integration.oauth2-demo') AND a.action_key='view'
ON CONFLICT(subject_type,subject_id,resource_id,action_id) WHERE status='ACTIVE' DO NOTHING;

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
  'user','user:'||u.canonical_user_id,'relation',g.relation,
  'object',CASE r.type WHEN 'APPLICATION' THEN 'application:'||regexp_replace(r.resource_key,'^application:','')
    WHEN 'EXTERNAL_RESOURCE' THEN 'external_resource:'||replace(regexp_replace(r.resource_key,'^external_resource:',''),':','/')
    ELSE 'resource:'||replace(r.resource_key,':','/') END),
  'development-administrator-grant:'||g.id
FROM authorization_grant g
JOIN external_identity e ON e.user_id=g.subject_id AND e.created_by='FIRST_ADMIN_BOOTSTRAP'
JOIN app_user u ON u.id=g.subject_id
JOIN resource r ON r.id=g.resource_id
WHERE g.subject_type='USER' AND g.status='ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM outbox_event o WHERE o.aggregate_type='grant' AND o.aggregate_id=g.id AND o.event_type='GRANT_WRITE')
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES('development-administrator-grants','1')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
COMMIT;
