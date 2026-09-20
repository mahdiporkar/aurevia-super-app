-- HR users must receive only the Superset assets explicitly assigned to them.
-- The old demo seed granted hr-user both the Reports application and the
-- Superset catalog root; that inherited access made every published asset
-- visible and bypassed per-user dashboard isolation.
WITH revoked AS (
  UPDATE authorization_grant g
  SET status='ARCHIVED', version=g.version+1
  FROM app_user u, resource resource, action action
  WHERE g.subject_type='USER' AND g.subject_id=u.id
    AND g.resource_id=resource.id AND g.action_id=action.id
    AND g.status='ACTIVE'
    AND u.external_id='hr-user'
    AND action.action_key='view'
    AND resource.resource_key IN ('application:aurevia/reports','external_resource:superset-public')
  RETURNING g.id, g.relation, resource.resource_key, u.canonical_user_id
)
INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',revoked.id,'GRANT_DELETE',jsonb_build_object(
  'user','user:'||revoked.canonical_user_id,
  'relation',revoked.relation,
  'object',revoked.resource_key),
  'hr-user-superset-broad-revoke-v77:'||revoked.id
FROM revoked
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES ('superset-role-model','5')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
