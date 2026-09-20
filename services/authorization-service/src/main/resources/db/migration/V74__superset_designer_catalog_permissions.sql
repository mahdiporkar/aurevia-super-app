-- Auth DB is the source of truth.  A single role grant on the Superset root is
-- projected through the existing outbox to OpenFGA, where inheritance gives a
-- designer VIEW and EDIT on every registered dashboard and chart.
INSERT INTO resource_action(resource_id,action_id)
SELECT resource.id,action.id
FROM resource CROSS JOIN action
WHERE resource.resource_key='external_resource:superset-public'
  AND action.action_key IN ('view','update')
ON CONFLICT DO NOTHING;

INSERT INTO authorization_grant(subject_type,subject_id,resource_id,action_id,relation)
SELECT 'ROLE',role.id,resource.id,action.id,
  CASE action.action_key WHEN 'view' THEN 'viewer' ELSE 'editor' END
FROM application_role role
JOIN resource ON resource.resource_key='external_resource:superset-public'
JOIN action ON action.action_key IN ('view','update')
WHERE role.role_key='superset-designer'
  AND NOT EXISTS (
    SELECT 1 FROM authorization_grant existing
    WHERE existing.subject_type='ROLE' AND existing.subject_id=role.id
      AND existing.resource_id=resource.id AND existing.action_id=action.id
      AND existing.status='ACTIVE'
  );

INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,payload,idempotency_key)
SELECT 'grant',g.id,'GRANT_WRITE',jsonb_build_object(
  'user','role:'||role.role_key||'#assignee',
  'relation',g.relation,
  'object','external_resource:superset-public'),
  'superset-designer-root-v74:'||g.id
FROM authorization_grant g
JOIN application_role role ON role.id=g.subject_id
JOIN resource ON resource.id=g.resource_id
WHERE g.subject_type='ROLE' AND g.status='ACTIVE'
  AND role.role_key='superset-designer'
  AND resource.resource_key='external_resource:superset-public'
ON CONFLICT(idempotency_key) DO NOTHING;

INSERT INTO schema_version(component,version) VALUES ('superset-role-model','2')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
