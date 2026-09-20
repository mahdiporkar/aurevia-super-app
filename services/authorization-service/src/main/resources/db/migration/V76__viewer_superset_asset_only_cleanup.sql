-- Viewer visibility must come only from individual Superset asset grants.
-- Remove legacy direct grants on the Reports application and Superset root;
-- the existing dashboard-level VIEW grant remains active.
WITH revoked AS (
  UPDATE authorization_grant g
  SET status='ARCHIVED', version=g.version+1
  FROM app_user u, resource resource, action action
  WHERE g.subject_type='USER' AND g.subject_id=u.id
    AND g.resource_id=resource.id AND g.action_id=action.id
    AND g.status='ACTIVE'
    AND u.external_id='viewer'
    AND action.action_key='view'
    AND resource.resource_key IN ('application:aurevia/reports','external_resource:superset-public')
  RETURNING g.id, g.relation, resource.resource_key
)
INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',revoked.id,'GRANT_DELETE',jsonb_build_object(
  'user','user:viewer',
  'relation',revoked.relation,
  'object',case when revoked.resource_key='application:aurevia/reports'
    then 'application:aurevia/reports' else 'external_resource:superset-public' end),
  'viewer-superset-broad-revoke-v76:'||revoked.id
FROM revoked
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES ('superset-role-model','4')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
