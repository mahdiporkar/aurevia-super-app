-- Viewer access is intentionally asset-scoped.  The old role-level Reports
-- application grant made every catalog card visible even when runtime access
-- correctly denied the dashboard.  Keep the role for identity compatibility,
-- but remove its broad Reports visibility grant.
WITH revoked AS (
  UPDATE authorization_grant g
  SET status='ARCHIVED', version=g.version+1
  FROM application_role role, resource resource, action action
  WHERE g.subject_type='ROLE' AND g.subject_id=role.id
    AND g.resource_id=resource.id AND g.action_id=action.id
    AND g.status='ACTIVE'
    AND role.role_key='superset-viewer'
    AND resource.resource_key='application:aurevia/reports'
    AND action.action_key='view'
  RETURNING g.id, g.relation
)
INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',revoked.id,'GRANT_DELETE',jsonb_build_object(
  'user','role:superset-viewer#assignee',
  'relation',revoked.relation,
  'object','application:aurevia/reports'),
  'superset-viewer-reports-revoke-v75:'||revoked.id
FROM revoked
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES ('superset-role-model','3')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
